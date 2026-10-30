package com.leo.erp.market.lengthpremium.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.market.lengthpremium.domain.entity.MdLengthPremium;
import com.leo.erp.market.lengthpremium.repository.MdLengthPremiumRepository;
import com.leo.erp.market.lengthpremium.web.dto.LengthPremiumRequest;
import com.leo.erp.market.lengthpremium.web.dto.LengthPremiumResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LengthPremiumService 极端情况测试：定尺唯一、状态白名单、空白与超长边界、
 * 负加价（优惠）允许、软删、未命中 404，以及加价映射只含「正常」状态并保持排序。
 */
@ExtendWith(MockitoExtension.class)
class LengthPremiumServiceTest {

    @Mock
    private MdLengthPremiumRepository repository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private LengthPremiumService service;

    private MdLengthPremium rule(long id, String lengthName, int premium, String status) {
        MdLengthPremium entity = new MdLengthPremium();
        entity.setId(id);
        entity.setLengthName(lengthName);
        entity.setPremium(premium);
        entity.setSortOrder((int) id);
        entity.setStatus(status);
        return entity;
    }

    private LengthPremiumRequest request(String lengthName, Integer premium, String status) {
        return new LengthPremiumRequest(lengthName, premium, 10, status, null, null);
    }

    private LengthPremiumRequest requestWithSuffix(String lengthName, Integer premium, String status,
                                                   String specSuffix) {
        return new LengthPremiumRequest(lengthName, premium, 10, status, null, specSuffix);
    }

    // ---------- create ----------

    @Test
    void create_shouldRejectDuplicateLengthName() {
        when(repository.existsByLengthNameAndDeletedFlagFalse("12米")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("12米", 30, StatusConstants.NORMAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在加价规则");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldRejectUnknownStatus() {
        assertThatThrownBy(() -> service.create(request("12米", 30, "停用")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态只能是");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldAllowNegativePremiumAsDiscount() {
        when(snowflakeIdGenerator.nextId()).thenReturn(9L);
        when(repository.saveAndFlush(any(MdLengthPremium.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LengthPremiumResponse response = service.create(request("9米", -15, StatusConstants.NORMAL));

        assertThat(response.premium()).isEqualTo(-15);
    }

    @Test
    void create_shouldAllowZeroPremium() {
        when(snowflakeIdGenerator.nextId()).thenReturn(9L);
        when(repository.saveAndFlush(any(MdLengthPremium.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(request("6米", 0, StatusConstants.NORMAL)).premium()).isZero();
    }

    @Test
    void create_shouldTrimLengthName() {
        when(snowflakeIdGenerator.nextId()).thenReturn(9L);
        when(repository.saveAndFlush(any(MdLengthPremium.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LengthPremiumResponse response = service.create(
                new LengthPremiumRequest("  12米  ", 30, 1, StatusConstants.NORMAL, "  备注  ", null));

        assertThat(response.lengthName()).isEqualTo("12米");
        assertThat(response.remark()).isEqualTo("备注");
    }

    // ---------- 打印规格后缀(specSuffix) ----------

    @Test
    void create_shouldPersistTrimmedSpecSuffixAndEchoIt() {
        when(snowflakeIdGenerator.nextId()).thenReturn(9L);
        when(repository.saveAndFlush(any(MdLengthPremium.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LengthPremiumResponse response = service.create(
                requestWithSuffix("12米", 30, StatusConstants.NORMAL, "  *12  "));

        assertThat(response.specSuffix()).isEqualTo("*12");
    }

    @Test
    void create_shouldNormalizeBlankSpecSuffixToNull() {
        when(snowflakeIdGenerator.nextId()).thenReturn(9L);
        when(repository.saveAndFlush(any(MdLengthPremium.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LengthPremiumResponse response = service.create(
                requestWithSuffix("9米", 0, StatusConstants.NORMAL, "   "));

        assertThat(response.specSuffix()).isNull();
    }

    @Test
    void update_shouldChangeSpecSuffix() {
        when(repository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(rule(7L, "12米", 30, StatusConstants.NORMAL)));
        when(repository.existsByLengthNameAndDeletedFlagFalseAndIdNot("12米", 7L)).thenReturn(false);
        when(repository.saveAndFlush(any(MdLengthPremium.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        LengthPremiumResponse response = service.update(7L,
                requestWithSuffix("12米", 30, StatusConstants.NORMAL, "*12m"));

        assertThat(response.specSuffix()).isEqualTo("*12m");
    }

    @Test
    void update_shouldClearSpecSuffixWhenBlank() {
        // 显式清空: 维护页提交空串 → spec_suffix 置 null(打印时不再追加后缀)
        MdLengthPremium entity = rule(7L, "12米", 30, StatusConstants.NORMAL);
        entity.setSpecSuffix("*12");
        when(repository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(entity));
        when(repository.existsByLengthNameAndDeletedFlagFalseAndIdNot("12米", 7L)).thenReturn(false);
        when(repository.saveAndFlush(any(MdLengthPremium.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        LengthPremiumResponse response = service.update(7L,
                requestWithSuffix("12米", 30, StatusConstants.NORMAL, ""));

        assertThat(response.specSuffix()).isNull();
    }

    @Test
    void update_shouldKeepSpecSuffixWhenNull() {
        // 与 remark 刻意不同: 旧版维护页不带本字段(null) 时必须保持原后缀, 不能静默抹掉回填的 *12
        MdLengthPremium entity = rule(7L, "12米", 30, StatusConstants.NORMAL);
        entity.setSpecSuffix("*12");
        when(repository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(entity));
        when(repository.existsByLengthNameAndDeletedFlagFalseAndIdNot("12米", 7L)).thenReturn(false);
        when(repository.saveAndFlush(any(MdLengthPremium.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        LengthPremiumResponse response = service.update(7L, request("12米", 45, StatusConstants.NORMAL));

        assertThat(response.premium()).isEqualTo(45);
        assertThat(response.specSuffix()).isEqualTo("*12");
    }

    @Test
    void create_shouldRejectBlankLengthName() {
        assertThatThrownBy(() -> service.create(request("   ", 30, StatusConstants.NORMAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("定尺名称不能为空");
    }

    @Test
    void create_shouldRejectOverlongLengthName() {
        assertThatThrownBy(() -> service.create(request("x".repeat(33), 30, StatusConstants.NORMAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过32个字符");
    }

    // ---------- update ----------

    @Test
    void update_shouldAllowKeepingOwnLengthName() {
        when(repository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(rule(7L, "12米", 30, StatusConstants.NORMAL)));
        when(repository.existsByLengthNameAndDeletedFlagFalseAndIdNot("12米", 7L)).thenReturn(false);
        when(repository.saveAndFlush(any(MdLengthPremium.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LengthPremiumResponse response = service.update(7L, request("12米", 45, StatusConstants.NORMAL));

        assertThat(response.premium()).isEqualTo(45);
    }

    @Test
    void update_shouldRejectLengthNameTakenByAnotherRecord() {
        when(repository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(rule(7L, "12米", 30, StatusConstants.NORMAL)));
        when(repository.existsByLengthNameAndDeletedFlagFalseAndIdNot("9米", 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L, request("9米", 30, StatusConstants.NORMAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他记录占用");
    }

    @Test
    void update_shouldThrow404WhenMissing() {
        when(repository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, request("12米", 30, StatusConstants.NORMAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("定尺加价规则不存在");
    }

    // ---------- delete / detail ----------

    @Test
    void delete_shouldSoftDelete() {
        MdLengthPremium entity = rule(7L, "12米", 30, StatusConstants.NORMAL);
        when(repository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any(MdLengthPremium.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.delete(7L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void detail_shouldMapAllFields() {
        MdLengthPremium entity = rule(7L, "12米", 30, StatusConstants.DISABLED);
        entity.setRemark("备注");
        entity.setSpecSuffix("*12");
        when(repository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(entity));

        LengthPremiumResponse response = service.detail(7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.lengthName()).isEqualTo("12米");
        assertThat(response.premium()).isEqualTo(30);
        assertThat(response.status()).isEqualTo(StatusConstants.DISABLED);
        assertThat(response.remark()).isEqualTo("备注");
        assertThat(response.specSuffix()).isEqualTo("*12");
    }

    // ---------- activePremiumMap ----------

    @Test
    void activePremiumMap_shouldReturnLengthToPremiumInSortOrder() {
        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(
                        rule(1L, "12米", 30, StatusConstants.NORMAL),
                        rule(2L, "9米", -15, StatusConstants.NORMAL)));

        Map<String, Integer> mapping = service.activePremiumMap();

        assertThat(mapping).containsExactly(
                Map.entry("12米", 30),
                Map.entry("9米", -15));
    }

    @Test
    void activePremiumMap_shouldBeEmptyWhenNoActiveRows() {
        // 表为空时返回空 Map, 调用方(SteelQuoteMatchService)据此回退 yml 与 Java 默认值
        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(StatusConstants.NORMAL))
                .thenReturn(List.of());

        assertThat(service.activePremiumMap()).isEmpty();
    }

    @Test
    void activePremiumMap_shouldSkipRowsWithNullPremium() {
        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(rule(1L, "12米", 30, StatusConstants.NORMAL), rule(2L, "9米", 0, StatusConstants.NORMAL)));

        assertThat(service.activePremiumMap()).containsKeys("12米", "9米");
    }

    // ---------- activeLengthNames / activeSpecSuffixMap ----------

    private MdLengthPremium ruleWithSuffix(long id, String lengthName, Integer premium, String suffix) {
        MdLengthPremium entity = rule(id, lengthName, premium == null ? 0 : premium, StatusConstants.NORMAL);
        entity.setSpecSuffix(suffix);
        return entity;
    }

    @Test
    void activeLengthNames_shouldReturnActiveNamesInMaintainedOrder() {
        // 与 V202 种子一致: 9米 排在 12米 之前
        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(
                        ruleWithSuffix(2L, "9米", 0, null),
                        ruleWithSuffix(1L, "12米", 30, "*12")));

        assertThat(service.activeLengthNames()).containsExactly("9米", "12米");
    }

    @Test
    void activeLengthNames_shouldBeEmptyWhenTableEmpty() {
        // 空集合表示定尺主数据未维护, 商品长度校验必须据此放行而不是拦截
        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(StatusConstants.NORMAL))
                .thenReturn(List.of());

        assertThat(service.activeLengthNames()).isEmpty();
    }

    @Test
    void activeSpecSuffixMap_shouldOnlyContainConfiguredSuffixes() {
        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(
                        ruleWithSuffix(2L, "9米", 0, null),
                        ruleWithSuffix(3L, "10米", 10, "  "),
                        ruleWithSuffix(1L, "12米", 30, " *12 ")));

        assertThat(service.activeSpecSuffixMap()).containsExactly(Map.entry("12米", "*12"));
    }

    @Test
    void activeSpecSuffixMap_shouldBeEmptyWhenNoSuffixConfigured() {
        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(ruleWithSuffix(1L, "12米", 30, null)));

        assertThat(service.activeSpecSuffixMap()).isEmpty();
        // 但定尺名称仍在, 打印解析据此判断「主数据已维护、只是没有后缀」
        assertThat(service.activeLengthNames()).isNotEmpty();
    }
}
