package com.leo.erp.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.UserListColumnSettingsPayload;
import com.leo.erp.auth.web.dto.UserSettingsPayload;
import com.leo.erp.auth.web.dto.UserSettingsSectionPayload;
import com.leo.erp.auth.web.dto.UserSettingsSectionUpdatePayload;
import com.leo.erp.auth.web.dto.UserSettingsUpdateRequest;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户界面设置的跨设备同步（《client-state-sync.md》§4 的服务端实现）。
 *
 * <p><b>存储</b>：复用 {@code sys_user.preferences_json}（JSONB，无 DDL）。同一个字段同时承载
 * 两代结构 —— v1 只有 {@code pages}，v2 是 {@code {version, sections, pages}}。读取按
 * {@code version} 判定，写入恒为 v2（老数据在被写入时自动升级）。</p>
 *
 * <p><b>合并语义</b>（唯一出口在本类，不要在其他地方再复制一份）：</p>
 * <ul>
 *   <li>覆盖型分片（{@code ui} / {@code layoutTabs} / 未知分片）：按 {@code clientUpdatedAt}
 *       做 last-write-wins。提交时间戳<b>严格早于</b>服务端已存时间戳 → 409（带回服务端值）；
 *       <b>相等且内容相同</b> → 视为同一次提交的重试，幂等返回、不重写（客户端重试不会
 *       被自己的写入判成冲突）。</li>
 *   <li>合并型分片 {@code supplierQuote}：其余字段仍按 LWW 覆盖，但 {@code materialTabs}
 *       按 supplierId（及其内部材质名）<b>深度合并</b>，两台设备各改不同供应商的材质时两者都保住。</li>
 *   <li>未提交的分片（含服务端不认识的未知分片）原样保留，新增一种分片不需要改服务端。</li>
 * </ul>
 *
 * <p><b>并发</b>：读改写全程持有 {@code sys_user} 行悲观锁
 * （{@code findByIdAndDeletedFlagFalseForUpdate}），同一用户的并发 PUT 串行化，不会丢更新；
 * {@code preferences_json} 用原生 UPDATE 直写，不参与 {@code @Version} 乐观锁（避免与登录审计
 * 等旁路写互相顶掉）。</p>
 */
@Slf4j
@Service
public class UserAccountSettingsService {

    /** 单个分片内容上限（序列化后的 UTF-8 字节数）。 */
    static final int SECTION_MAX_BYTES = 32 * 1024;
    /** 整份设置文档（含所有分片与 pages）上限。 */
    static final int TOTAL_MAX_BYTES = 256 * 1024;
    /** 单次提交的分片数量上限，防「分片名爆炸」把 JSONB 撑坏。 */
    private static final int MAX_SECTION_COUNT = 20;

    /** 用于把字节数换算成 KB 展示（错误文案里给用户看的单位）。 */
    private static final int BYTES_PER_KB = 1024;

    private static final int MIN_COLUMN_WIDTH = 80;
    private static final int MAX_COLUMN_WIDTH = 800;
    private static final int MAX_PAGE_COUNT = 200;
    private static final int MAX_SECTION_KEY_LENGTH = 64;

    private static final String SUPPLIER_QUOTE_SECTION = "supplierQuote";
    private static final String MATERIAL_TABS_FIELD = "materialTabs";
    private static final String KEY_VERSION = "version";
    private static final String KEY_SECTIONS = "sections";
    private static final String KEY_PAGES = "pages";
    private static final String KEY_VALUE = "value";
    private static final String KEY_CLIENT_UPDATED_AT = "clientUpdatedAt";
    private static final String KEY_SERVER_UPDATED_AT = "serverUpdatedAt";

    private static final TypeReference<Map<String, UserListColumnSettingsPayload>> PAGES_TYPE =
            new TypeReference<>() {
            };

    private final UserAccountRepository repository;
    private final ObjectMapper objectMapper;

    public UserAccountSettingsService(UserAccountRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 读取完整设置（v2 结构，兼容库里的 v1 旧数据）。
     *
     * <p>只读，不加锁、不做 v1→v2 落库升级：升级发生在下一次写入，读路径保持无副作用。</p>
     */
    @Transactional(readOnly = true)
    public UserSettingsPayload getSettings(Long userId) {
        return toPayload(readStored(getEntity(userId)));
    }

    /**
     * 按分片/列设置增量保存，返回合并后的完整结构。
     *
     * <p>sections 走 LWW（服务端已存分片更新更晚则 409，整体不落库）；pages 是整段替换、
     * 没有时间戳，不参与 LWW。两者可以出现在同一次请求里，但任一项失败（含 sections 409）
     * 都不会写入任何一部分，避免「sections 冲突但 pages 被写进去」的半成功状态。</p>
     *
     * @param request 只含本次变化的部分；{@code null} 或全空表示无变化
     * @throws UserSettingsConflictException 服务端已存分片比本次提交更新（409）
     * @throws BusinessException             分片/总量超限（413）、用户不存在（404）、时间戳非法（422）
     */
    @Transactional
    public UserSettingsPayload saveSettings(Long userId, UserSettingsUpdateRequest request) {
        UserAccount account = getEntityForUpdate(userId);
        StoredSettings current = readStored(account);
        Map<String, StoredSection> mergedSections = new LinkedHashMap<>(current.sections());
        List<String> conflictSections = new ArrayList<>();
        Map<String, UserSettingsSectionPayload> conflictServerSections = new LinkedHashMap<>();
        boolean changed = false;

        Map<String, UserSettingsSectionUpdatePayload> incomingSections =
                request == null || request.sections() == null ? Map.of() : request.sections();
        if (incomingSections.size() > MAX_SECTION_COUNT) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "单次提交的设置分片数量不能超过 " + MAX_SECTION_COUNT + " 项");
        }

        for (Map.Entry<String, UserSettingsSectionUpdatePayload> entry : incomingSections.entrySet()) {
            String sectionKey = requireSectionKey(entry.getKey());
            UserSettingsSectionUpdatePayload incoming = entry.getValue();
            if (incoming == null || incoming.value() == null || incoming.value().isNull()) {
                // DTO 上的 @NotNull 在完整上下文里会先拦一道；这里再兜一次，保证任意调用方
                // （含直连 service 的单测/后续内部调用）都不会把 null 分片写进 JSONB。
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "设置分片 " + sectionKey + " 的提交内容不能为空");
            }
            Instant incomingAt = parseClientUpdatedAt(sectionKey, incoming.clientUpdatedAt());
            StoredSection existing = mergedSections.get(sectionKey);

            if (existing != null) {
                Instant existingAt = parseStoredUpdatedAt(sectionKey, existing.clientUpdatedAt());
                if (existingAt != null && incomingAt.isBefore(existingAt)) {
                    conflictSections.add(sectionKey);
                    conflictServerSections.put(sectionKey, toSectionPayload(existing));
                    continue;
                }
                if (existingAt != null && incomingAt.equals(existingAt)
                        && jsonEquals(existing.value(), incoming.value())) {
                    // 时间戳与内容都没变：同一次提交的重试，幂等返回，不刷新 serverUpdatedAt。
                    continue;
                }
            }

            JsonNode mergedValue = mergeSectionValue(sectionKey,
                    existing == null ? null : existing.value(), incoming.value());
            assertSectionWithinLimit(sectionKey, mergedValue);
            mergedSections.put(sectionKey, new StoredSection(
                    mergedValue, incoming.clientUpdatedAt().trim(), Instant.now().toString()));
            changed = true;
        }

        // sections 冲突优先：此时 pages 也不写，避免同一次请求里出现半成功状态。
        if (!conflictSections.isEmpty()) {
            throw new UserSettingsConflictException(conflictSections, conflictServerSections);
        }

        Map<String, UserListColumnSettingsPayload> mergedPages = current.pages();
        if (request != null && request.pages() != null) {
            // pages 没有时间戳：提交即整段替换（v1 savePreferences 语义），不做 LWW / 逐页合并。
            Map<String, UserListColumnSettingsPayload> incomingPages = normalizePages(request.pages());
            if (!jsonEquals(pagesNode(incomingPages), pagesNode(current.pages()))) {
                mergedPages = incomingPages;
                changed = true;
            }
        }

        if (!changed) {
            // 空请求体或全部为幂等重试：不回写，避免无意义的行更新与审计噪声。
            return toPayload(current);
        }

        StoredSettings next = new StoredSettings(current.root(), mergedSections, mergedPages);
        persist(userId, serialize(next));
        return toPayload(next);
    }

    private UserAccount getEntity(Long userId) {
        return repository.findByIdAndDeletedFlagFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private UserAccount getEntityForUpdate(Long userId) {
        return repository.findByIdAndDeletedFlagFalseForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private void persist(Long userId, String preferencesJson) {
        assertTotalWithinLimit(preferencesJson);
        int updated = repository.updatePreferencesJson(userId, preferencesJson);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    // ---------------------------------------------------------------------
    // 读取
    // ---------------------------------------------------------------------

    /** 存库结构的内存视图；{@code root} 保留未知顶层字段，写回时只替换 version/sections/pages。 */
    private record StoredSettings(ObjectNode root,
                                  Map<String, StoredSection> sections,
                                  Map<String, UserListColumnSettingsPayload> pages) {
    }

    /** 单个分片的存库形态（{@code serverUpdatedAt} 可能为空：v2 早期数据或手工写入）。 */
    private record StoredSection(JsonNode value, String clientUpdatedAt, String serverUpdatedAt) {
    }

    /**
     * 解析 {@code preferences_json}。
     *
     * <p>两级降级：JSON 本身解析失败、或根节点不是对象，都按「空 v1」处理并打 warn ——
     * 一条坏数据不能把用户设置永久卡死（旧实现就是这么降级的，行为保持一致）。</p>
     */
    private StoredSettings readStored(UserAccount account) {
        ObjectNode emptyRoot = objectMapper.createObjectNode();
        String raw = account.getPreferencesJson();
        if (raw == null || raw.isBlank()) {
            return new StoredSettings(emptyRoot, Map.of(), Map.of());
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (JsonProcessingException ex) {
            log.warn("解析用户偏好配置失败，按空配置处理, userId={}", account.getId(), ex);
            return new StoredSettings(emptyRoot, Map.of(), Map.of());
        }
        if (root == null || !root.isObject()) {
            log.warn("用户偏好配置根节点不是对象，按空配置处理, userId={}", account.getId());
            return new StoredSettings(emptyRoot, Map.of(), Map.of());
        }

        ObjectNode objectRoot = (ObjectNode) root;
        return new StoredSettings(objectRoot,
                readSections(objectRoot.get(KEY_SECTIONS), account.getId()),
                readPages(objectRoot.get(KEY_PAGES), account.getId()));
    }

    private Map<String, StoredSection> readSections(JsonNode sectionsNode, Long userId) {
        if (sectionsNode == null || !sectionsNode.isObject()) {
            return Map.of();
        }
        Map<String, StoredSection> sections = new LinkedHashMap<>();
        sectionsNode.fields().forEachRemaining(entry -> {
            String sectionKey = normalizeKey(entry.getKey());
            JsonNode node = entry.getValue();
            if (sectionKey == null || sectionKey.length() > MAX_SECTION_KEY_LENGTH
                    || node == null || !node.isObject()) {
                log.warn("忽略非法的设置分片, userId={}, section={}", userId, entry.getKey());
                return;
            }
            JsonNode value = node.get(KEY_VALUE);
            sections.put(sectionKey, new StoredSection(
                    value == null ? NullNode.getInstance() : value,
                    textOrNull(node.get(KEY_CLIENT_UPDATED_AT)),
                    textOrNull(node.get(KEY_SERVER_UPDATED_AT))));
        });
        return sections;
    }

    private Map<String, UserListColumnSettingsPayload> readPages(JsonNode pagesNode, Long userId) {
        if (pagesNode == null || !pagesNode.isObject()) {
            return Map.of();
        }
        try {
            Map<String, UserListColumnSettingsPayload> raw = objectMapper.convertValue(pagesNode, PAGES_TYPE);
            return normalizePages(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("用户列表页设置结构非法，按空配置处理, userId={}", userId, ex);
            return Map.of();
        }
    }

    // ---------------------------------------------------------------------
    // 写回
    // ---------------------------------------------------------------------

    private String serialize(StoredSettings settings) {
        ObjectNode root = settings.root().deepCopy();
        root.put(KEY_VERSION, UserSettingsPayload.CURRENT_VERSION);

        ObjectNode sectionsNode = root.putObject(KEY_SECTIONS);
        settings.sections().forEach((sectionKey, section) -> {
            ObjectNode sectionNode = sectionsNode.putObject(sectionKey);
            sectionNode.set(KEY_VALUE,
                    section.value() == null ? NullNode.getInstance() : section.value());
            if (section.clientUpdatedAt() != null) {
                sectionNode.put(KEY_CLIENT_UPDATED_AT, section.clientUpdatedAt());
            }
            if (section.serverUpdatedAt() != null) {
                sectionNode.put(KEY_SERVER_UPDATED_AT, section.serverUpdatedAt());
            }
        });

        ObjectNode pagesNode = root.putObject(KEY_PAGES);
        settings.pages().forEach((pageKey, page) -> pagesNode.set(pageKey, objectMapper.valueToTree(page)));

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化用户设置失败", ex);
        }
    }

    private UserSettingsPayload toPayload(StoredSettings settings) {
        Map<String, UserSettingsSectionPayload> sections = new LinkedHashMap<>();
        settings.sections().forEach((key, section) -> sections.put(key, toSectionPayload(section)));
        return new UserSettingsPayload(UserSettingsPayload.CURRENT_VERSION, sections, settings.pages());
    }

    private UserSettingsSectionPayload toSectionPayload(StoredSection section) {
        return new UserSettingsSectionPayload(section.value(), section.clientUpdatedAt(),
                section.serverUpdatedAt());
    }

    // ---------------------------------------------------------------------
    // 合并
    // ---------------------------------------------------------------------

    /**
     * 分片级合并：只有 {@code supplierQuote.materialTabs} 需要深度合并，其余一律整段覆盖。
     *
     * <p>深度合并方向是「以服务端为底、客户端覆盖」：客户端提交的是它本地看到的最新一份，
     * 服务端保留客户端没提到、但另一台设备已经写过的供应商条目。</p>
     */
    private JsonNode mergeSectionValue(String sectionKey, JsonNode existingValue, JsonNode incomingValue) {
        if (!SUPPLIER_QUOTE_SECTION.equals(sectionKey)
                || existingValue == null
                || !existingValue.isObject()
                || !incomingValue.isObject()) {
            return incomingValue;
        }
        ObjectNode merged = ((ObjectNode) incomingValue).deepCopy();
        JsonNode existingTabs = existingValue.get(MATERIAL_TABS_FIELD);
        if (existingTabs == null || !existingTabs.isObject()) {
            return merged;
        }
        ObjectNode tabs = ((ObjectNode) existingTabs).deepCopy();
        JsonNode incomingTabs = incomingValue.get(MATERIAL_TABS_FIELD);
        if (incomingTabs != null && incomingTabs.isObject()) {
            deepMerge(tabs, (ObjectNode) incomingTabs);
        }
        merged.set(MATERIAL_TABS_FIELD, tabs);
        return merged;
    }

    /**
     * 递归合并对象：两边都是对象则继续下钻，否则客户端值覆盖。
     *
     * <p>不做数组元素级合并（{@code openSupplierIds} 这类列表整体覆盖）：列表元素没有稳定身份，
     * 元素级合并会把删除动作吃掉。已知取舍：删除某个材质键不会被合并传播，需要显式清空该
     * 供应商条目。</p>
     */
    private void deepMerge(ObjectNode target, ObjectNode overlay) {
        overlay.fields().forEachRemaining(field -> {
            JsonNode targetChild = target.get(field.getKey());
            JsonNode overlayChild = field.getValue();
            if (targetChild != null && targetChild.isObject() && overlayChild.isObject()) {
                deepMerge((ObjectNode) targetChild, (ObjectNode) overlayChild);
            } else {
                target.set(field.getKey(), overlayChild.deepCopy());
            }
        });
    }

    private boolean jsonEquals(JsonNode left, JsonNode right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.equals(right);
    }

    /** pages 的 JSON 视图：仅用于幂等比较（Map 顺序不敏感，列数组顺序敏感）。 */
    private JsonNode pagesNode(Map<String, UserListColumnSettingsPayload> pages) {
        return objectMapper.valueToTree(pages);
    }

    // ---------------------------------------------------------------------
    // 时间戳
    // ---------------------------------------------------------------------

    /** 解析本次提交的 {@code clientUpdatedAt}：非法格式直接 422，不静默当成「最旧」。 */
    private Instant parseClientUpdatedAt(String sectionKey, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "设置分片 " + sectionKey + " 缺少 clientUpdatedAt");
        }
        Instant parsed = tryParseInstant(raw);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "设置分片 " + sectionKey + " 的 clientUpdatedAt 不是合法的 ISO-8601 时间："
                            + raw.trim());
        }
        return parsed;
    }

    /** 解析已存库的 {@code clientUpdatedAt}：坏数据按「无基线」处理（本次提交直接生效），不阻断写入。 */
    private Instant parseStoredUpdatedAt(String sectionKey, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Instant parsed = tryParseInstant(raw);
        if (parsed == null) {
            log.warn("忽略非法的时间戳, section={}, value={}", sectionKey, raw);
        }
        return parsed;
    }

    private Instant tryParseInstant(String raw) {
        String text = raw.trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            // 继续尝试带偏移的形态（如 2026-11-14T15:05:00+08:00）。
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // 限额
    // ---------------------------------------------------------------------

    private void assertSectionWithinLimit(String sectionKey, JsonNode value) {
        int bytes = jsonBytes(value);
        if (bytes > SECTION_MAX_BYTES) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "设置分片 " + sectionKey + " 超过 " + (SECTION_MAX_BYTES / BYTES_PER_KB) + "KB 上限（当前 "
                            + bytes + " 字节）");
        }
    }

    private void assertTotalWithinLimit(String preferencesJson) {
        int bytes = preferencesJson.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > TOTAL_MAX_BYTES) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "用户设置总量超过 " + (TOTAL_MAX_BYTES / BYTES_PER_KB) + "KB 上限（当前 " + bytes + " 字节）");
        }
    }

    private int jsonBytes(JsonNode value) {
        if (value == null) {
            return 0;
        }
        try {
            return objectMapper.writeValueAsBytes(value).length;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化设置分片失败", ex);
        }
    }

    // ---------------------------------------------------------------------
    // 列表页设置规整（v1 行为原样保留：key 去空白、列宽夹在 80~800、空值丢弃）
    // ---------------------------------------------------------------------

    private Map<String, UserListColumnSettingsPayload> normalizePages(
            Map<String, UserListColumnSettingsPayload> pages) {
        if (pages == null || pages.isEmpty()) {
            return Map.of();
        }
        Map<String, UserListColumnSettingsPayload> normalizedPages = new LinkedHashMap<>();
        for (Map.Entry<String, UserListColumnSettingsPayload> entry : pages.entrySet()) {
            String pageKey = normalizeKey(entry.getKey());
            if (pageKey == null || normalizedPages.size() >= MAX_PAGE_COUNT) {
                continue;
            }
            normalizedPages.put(pageKey, normalizePageSettings(entry.getValue()));
        }
        return normalizedPages;
    }

    private UserListColumnSettingsPayload normalizePageSettings(UserListColumnSettingsPayload settings) {
        if (settings == null) {
            return new UserListColumnSettingsPayload(List.of(), List.of(), Map.of());
        }
        return new UserListColumnSettingsPayload(
                normalizeKeys(settings.orderedKeys()),
                normalizeKeys(settings.hiddenKeys()),
                normalizeColumnSizes(settings.columnSizes())
        );
    }

    private Map<String, Integer> normalizeColumnSizes(Map<String, Integer> columnSizes) {
        if (columnSizes == null || columnSizes.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : columnSizes.entrySet()) {
            String key = normalizeKey(entry.getKey());
            Integer value = entry.getValue();
            if (key == null || value == null || value <= 0) {
                continue;
            }
            normalized.put(key, Math.min(Math.max(value, MIN_COLUMN_WIDTH), MAX_COLUMN_WIDTH));
        }
        return normalized;
    }

    private List<String> normalizeKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalizedKeys = new LinkedHashSet<>();
        for (String key : keys) {
            String normalized = normalizeKey(key);
            if (normalized != null) {
                normalizedKeys.add(normalized);
            }
        }
        return List.copyOf(normalizedKeys);
    }

    /** 提交侧的 key 校验：空白 / 超长直接 422，避免把异常分片名写进 JSONB。 */
    private String requireSectionKey(String value) {
        String normalized = normalizeKey(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "设置分片的 key 不能为空");
        }
        if (normalized.length() > MAX_SECTION_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "设置分片的 key 长度不能超过64");
        }
        return normalized;
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
