package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentBindingRepository;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentBindingServiceTest {

    @BeforeEach
    void setUp() {
        authenticate(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReplaceBindingsAndPreserveAttachmentOrder() {
        AtomicReference<List<AttachmentBinding>> savedBindings = new AtomicReference<>(List.of());
        AtomicReference<List<AttachmentBinding>> deletedBindings = new AtomicReference<>(List.of());
        AtomicReference<Boolean> flushCalled = new AtomicReference<>(false);
        List<AttachmentBinding> existingBindings = List.of(binding(90L, "sales-order", 9L, 10L, 1));
        Map<Long, AttachmentView> attachments = new LinkedHashMap<>();
        attachments.put(11L, attachment(11L, "A.pdf"));
        attachments.put(12L, attachment(12L, "B.pdf"));

        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(existingBindings, savedBindings, deletedBindings, flushCalled),
                attachmentService(attachments),
                enabledUploadRuleService(),
                new FixedIdGenerator(101L, 102L),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        List<AttachmentView> result = service.replace("sales-order", 9L, List.of(12L, 11L));

        assertThat(savedBindings.get())
                .extracting(AttachmentBinding::getId, AttachmentBinding::getModuleKey, AttachmentBinding::getRecordId, AttachmentBinding::getAttachmentId, AttachmentBinding::getSortOrder)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(101L, "sales-order", 9L, 12L, 1),
                        org.assertj.core.groups.Tuple.tuple(102L, "sales-order", 9L, 11L, 2)
                );
        assertThat(deletedBindings.get()).containsExactlyElementsOf(existingBindings);
        assertThat(flushCalled.get()).isTrue();
        assertThat(result).extracting(AttachmentView::id).containsExactly(12L, 11L);
    }

    @Test
    void shouldRejectDuplicateAttachmentIds() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(101L),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        assertThatThrownBy(() -> service.replace("sales-order", 9L, List.of(12L, 12L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件列表存在重复项");
    }

    @Test
    void shouldReturnEmptyList_whenPageUploadDisabled_forList() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                disabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        List<AttachmentView> result = service.list("sales-order", 9L);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAttachments_whenPageUploadEnabled_forList() {
        List<AttachmentBinding> bindings = List.of(binding(1L, "sales-order", 9L, 10L, 1));
        Map<Long, AttachmentView> attachments = new LinkedHashMap<>();
        attachments.put(10L, attachment(10L, "test.pdf"));

        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(bindings, new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(attachments),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        List<AttachmentView> result = service.list("sales-order", 9L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
    }

    @Test
    void shouldReturnEmptyList_whenPageUploadDisabled_forReplace() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                disabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        List<AttachmentView> result = service.replace("sales-order", 9L, List.of(10L));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReplaceWithEmptyList_whenNoNewAttachments() {
        AtomicReference<List<AttachmentBinding>> savedBindings = new AtomicReference<>(List.of());
        AtomicReference<List<AttachmentBinding>> deletedBindings = new AtomicReference<>(List.of());
        AtomicReference<Boolean> flushCalled = new AtomicReference<>(false);
        List<AttachmentBinding> existingBindings = List.of(binding(90L, "sales-order", 9L, 10L, 1));

        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(existingBindings, savedBindings, deletedBindings, flushCalled),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        List<AttachmentView> result = service.replace("sales-order", 9L, List.of());

        assertThat(deletedBindings.get()).containsExactlyElementsOf(existingBindings);
        assertThat(savedBindings.get()).isEmpty();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotDelete_whenNoExistingBindings() {
        AtomicReference<List<AttachmentBinding>> savedBindings = new AtomicReference<>(List.of());
        AtomicReference<List<AttachmentBinding>> deletedBindings = new AtomicReference<>(List.of());
        AtomicReference<Boolean> flushCalled = new AtomicReference<>(false);
        Map<Long, AttachmentView> attachments = new LinkedHashMap<>();
        attachments.put(10L, attachment(10L, "test.pdf"));

        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), savedBindings, deletedBindings, flushCalled),
                attachmentService(attachments),
                enabledUploadRuleService(),
                new FixedIdGenerator(101L),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        List<AttachmentView> result = service.replace("sales-order", 9L, List.of(10L));

        assertThat(deletedBindings.get()).isEmpty();
        assertThat(flushCalled.get()).isFalse();
        assertThat(savedBindings.get()).hasSize(1);
        assertThat(result).hasSize(1);
    }

    @Test
    void shouldReturnAttachmentIds_forListAttachmentIds() {
        List<AttachmentBinding> bindings = List.of(
                binding(1L, "sales-order", 9L, 10L, 1),
                binding(2L, "sales-order", 9L, 11L, 2)
        );

        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(bindings, new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        List<Long> result = service.listAttachmentIds("sales-order", 9L);

        assertThat(result).containsExactly(10L, 11L);
    }

    @Test
    void shouldReturnEmptyList_forListAttachmentIds_whenNoBindings() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        List<Long> result = service.listAttachmentIds("sales-order", 9L);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyMap_forListByRecordIds_whenRecordIdsNull() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        Map<Long, List<AttachmentView>> result = service.listByRecordIds("sales-order", null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyMap_forListByRecordIds_whenRecordIdsEmpty() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        Map<Long, List<AttachmentView>> result = service.listByRecordIds("sales-order", List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyMap_forListByRecordIds_whenAllIdsInvalid() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        Map<Long, List<AttachmentView>> result = service.listByRecordIds("sales-order", List.of(0L, -1L));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnGroupedAttachments_forListByRecordIds() {
        List<AttachmentBinding> bindings = List.of(
                binding(1L, "sales-order", 1L, 10L, 1),
                binding(2L, "sales-order", 1L, 11L, 2),
                binding(3L, "sales-order", 2L, 12L, 1)
        );
        Map<Long, AttachmentView> attachments = new LinkedHashMap<>();
        attachments.put(10L, attachment(10L, "A.pdf"));
        attachments.put(11L, attachment(11L, "B.pdf"));
        attachments.put(12L, attachment(12L, "C.pdf"));

        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(bindings, new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(attachments),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        Map<Long, List<AttachmentView>> result = service.listByRecordIds("sales-order", List.of(1L, 2L, 3L));

        assertThat(result).hasSize(2);
        assertThat(result.get(1L)).extracting(AttachmentView::id).containsExactly(10L, 11L);
        assertThat(result.get(2L)).extracting(AttachmentView::id).containsExactly(12L);
    }

    @Test
    void shouldDeduplicateRecordIds_forListByRecordIds() {
        List<AttachmentBinding> bindings = List.of(
                binding(1L, "sales-order", 1L, 10L, 1)
        );
        Map<Long, AttachmentView> attachments = new LinkedHashMap<>();
        attachments.put(10L, attachment(10L, "A.pdf"));

        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(bindings, new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(attachments),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        Map<Long, List<AttachmentView>> result = service.listByRecordIds("sales-order", List.of(1L, 1L, 1L));

        assertThat(result).hasSize(1);
        assertThat(result.get(1L)).hasSize(1);
    }

    @Test
    void shouldReturnAttachmentCounts_forCountByRecordIds() {
        List<AttachmentBinding> bindings = List.of(
                binding(1L, "sales-order", 1L, 10L, 1),
                binding(2L, "sales-order", 1L, 11L, 2),
                binding(3L, "sales-order", 2L, 12L, 1)
        );

        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(bindings, new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        Map<Long, Integer> result = service.countByRecordIds("sales-order", List.of(1L, 2L, 3L, 1L));

        assertThat(result).containsExactly(
                Map.entry(1L, 2),
                Map.entry(2L, 1),
                Map.entry(3L, 0)
        );
    }

    @Test
    void shouldRejectNullModuleKey() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        assertThatThrownBy(() -> service.list(null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少模块标识");
    }

    @Test
    void shouldRejectBlankModuleKey() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        assertThatThrownBy(() -> service.list("  ", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少模块标识");
    }

    @Test
    void shouldRejectInvalidModuleKey() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        assertThatThrownBy(() -> service.list("non-existent-module", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模块标识不合法");
    }

    @Test
    void shouldRejectNullRecordId() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        assertThatThrownBy(() -> service.list("sales-order", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少业务记录标识");
    }

    @Test
    void shouldRejectZeroRecordId() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        assertThatThrownBy(() -> service.list("sales-order", 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少业务记录标识");
    }

    @Test
    void shouldRejectNegativeRecordId() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        assertThatThrownBy(() -> service.list("sales-order", -1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少业务记录标识");
    }

    @Test
    void shouldRejectNullAttachmentIds_forReplace() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        assertThatThrownBy(() -> service.replace("sales-order", 1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件列表不能为空");
    }

    @Test
    void shouldRejectNullAttachmentIdInList_forReplace() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        assertThatThrownBy(() -> service.replace("sales-order", 1L, java.util.Arrays.asList(1L, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件ID不合法");
    }

    @Test
    void shouldRejectZeroAttachmentIdInList_forReplace() {
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(Map.of()),
                enabledUploadRuleService(),
                new FixedIdGenerator(),
                new ModuleCatalog(),
                attachmentFileRepository()
        );

        assertThatThrownBy(() -> service.replace("sales-order", 1L, List.of(1L, 0L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件ID不合法");
    }

    @Test
    void shouldRejectBindingUnboundAttachmentUploadedByOtherUser() {
        Map<Long, AttachmentView> attachments = Map.of(10L, attachment(10L, "test.pdf"));
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(attachments),
                enabledUploadRuleService(),
                new FixedIdGenerator(101L),
                new ModuleCatalog(),
                attachmentFileRepository(List.of(attachmentFile(10L, 2L)))
        );

        assertThatThrownBy(() -> service.replace("sales-order", 9L, List.of(10L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件不属于当前用户或当前业务记录");
    }

    @Test
    void shouldRejectBindingAttachmentFromAnotherRecord() {
        Map<Long, AttachmentView> attachments = Map.of(10L, attachment(10L, "test.pdf"));
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(List.of(binding(90L, "sales-order", 8L, 10L, 1)), new AtomicReference<>(List.of()), new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(attachments),
                enabledUploadRuleService(),
                new FixedIdGenerator(101L),
                new ModuleCatalog(),
                attachmentFileRepository(List.of(attachmentFile(10L, 1L)))
        );

        assertThatThrownBy(() -> service.replace("sales-order", 9L, List.of(10L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件不属于当前用户或当前业务记录");
    }

    @Test
    void shouldAllowRebindingAttachmentAlreadyOnSameRecord() {
        AtomicReference<List<AttachmentBinding>> savedBindings = new AtomicReference<>(List.of());
        List<AttachmentBinding> existingBindings = List.of(binding(90L, "sales-order", 9L, 10L, 1));
        Map<Long, AttachmentView> attachments = Map.of(10L, attachment(10L, "test.pdf"));
        AttachmentBindingService service = new AttachmentBindingService(
                bindingRepository(existingBindings, savedBindings, new AtomicReference<>(List.of()), new AtomicReference<>(false)),
                attachmentService(attachments),
                enabledUploadRuleService(),
                new FixedIdGenerator(101L),
                new ModuleCatalog(),
                attachmentFileRepository(List.of(attachmentFile(10L, 2L)))
        );

        List<AttachmentView> result = service.replace("sales-order", 9L, List.of(10L));

        assertThat(result).extracting(AttachmentView::id).containsExactly(10L);
        assertThat(savedBindings.get()).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    private AttachmentBindingRepository bindingRepository(List<AttachmentBinding> existingBindings,
                                                          AtomicReference<List<AttachmentBinding>> savedBindings,
                                                          AtomicReference<List<AttachmentBinding>> deletedBindings,
                                                          AtomicReference<Boolean> flushCalled) {
        return (AttachmentBindingRepository) Proxy.newProxyInstance(
                AttachmentBindingRepository.class.getClassLoader(),
                new Class[]{AttachmentBindingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByModuleKeyAndRecordIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc" -> existingBindings;
                    case "findByModuleKeyAndRecordIdInAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc" -> existingBindings;
                    case "findByAttachmentIdAndDeletedFlagFalseOrderByModuleKeyAscRecordIdAscSortOrderAscIdAsc" -> {
                        Long attachmentId = (Long) args[0];
                        yield existingBindings.stream()
                                .filter(binding -> attachmentId.equals(binding.getAttachmentId()))
                                .toList();
                    }
                    case "deleteAllInBatch" -> {
                        deletedBindings.set(new ArrayList<>((Collection<AttachmentBinding>) args[0]));
                        yield null;
                    }
                    case "flush" -> {
                        flushCalled.set(true);
                        yield null;
                    }
                    case "saveAll" -> {
                        savedBindings.set(new ArrayList<>((Collection<AttachmentBinding>) args[0]));
                        yield args[0];
                    }
                    case "toString" -> "AttachmentBindingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private AttachmentFileRepository attachmentFileRepository() {
        return attachmentFileRepository(List.of());
    }

    @SuppressWarnings("unchecked")
    private AttachmentFileRepository attachmentFileRepository(List<AttachmentFile> files) {
        return (AttachmentFileRepository) Proxy.newProxyInstance(
                AttachmentFileRepository.class.getClassLoader(),
                new Class[]{AttachmentFileRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAllByIdInAndCreatedByAndDeletedFlagFalse" -> {
                        Collection<Long> ids = (Collection<Long>) args[0];
                        Long createdBy = (Long) args[1];
                        if (files.isEmpty()) {
                            yield ids.stream().map(id -> attachmentFile(id, createdBy)).toList();
                        }
                        yield files.stream()
                                .filter(file -> ids.contains(file.getId()) && createdBy.equals(file.getCreatedBy()))
                                .toList();
                    }
                    case "toString" -> "AttachmentFileRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private AttachmentBinding binding(Long id, String moduleKey, Long recordId, Long attachmentId, Integer sortOrder) {
        AttachmentBinding binding = new AttachmentBinding();
        binding.setId(id);
        binding.setModuleKey(moduleKey);
        binding.setRecordId(recordId);
        binding.setAttachmentId(attachmentId);
        binding.setSortOrder(sortOrder);
        return binding;
    }

    private AttachmentFile attachmentFile(Long id, Long createdBy) {
        AttachmentFile file = new AttachmentFile();
        file.setId(id);
        file.setCreatedBy(createdBy);
        return file;
    }

    private void authenticate(Long userId) {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(userId, "tester", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private AttachmentService attachmentService(Map<Long, AttachmentView> attachments) {
        return new AttachmentService(null, null, null, null, null, null, null, null) {
            @Override
            public void validateAttachmentIds(List<Long> ids) {
            }

            @Override
            public List<AttachmentView> getAttachments(List<Long> ids, String moduleKey) {
                return ids.stream().map(attachments::get).toList();
            }

            @Override
            public Map<Long, AttachmentView> getAttachmentMap(List<Long> ids, String moduleKey) {
                Map<Long, AttachmentView> result = new LinkedHashMap<>();
                for (Long id : ids) {
                    AttachmentView attachment = attachments.get(id);
                    if (attachment != null) {
                        result.put(id, attachment);
                    }
                }
                return result;
            }
        };
    }

    private UploadRuleService enabledUploadRuleService() {
        UploadRuleService service = Mockito.mock(UploadRuleService.class);
        Mockito.when(service.isPageUploadEnabled(Mockito.anyString())).thenReturn(true);
        return service;
    }

    private UploadRuleService disabledUploadRuleService() {
        UploadRuleService service = Mockito.mock(UploadRuleService.class);
        Mockito.when(service.isPageUploadEnabled(Mockito.anyString())).thenReturn(false);
        return service;
    }

    private AttachmentView attachment(Long id, String name) {
        return new AttachmentView(
                id,
                name,
                name,
                name,
                "application/pdf",
                1L,
                "PAGE_UPLOAD",
                "tester",
                LocalDateTime.now(),
                true,
                "pdf",
                "/api/attachment/" + id + "/preview",
                "/api/attachment/" + id + "/download"
        );
    }

    private static final class FixedIdGenerator extends SnowflakeIdGenerator {

        private final long[] ids;
        private int index;

        private FixedIdGenerator(long... ids) {
            this.ids = ids;
        }

        @Override
        public synchronized long nextId() {
            return ids[index++];
        }
    }
}
