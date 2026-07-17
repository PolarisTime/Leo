package com.leo.erp.system.generalsetting.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.system.generalsetting.domain.entity.GeneralSetting;
import com.leo.erp.system.generalsetting.mapper.GeneralSettingMapper;
import com.leo.erp.system.generalsetting.repository.GeneralSettingRepository;
import com.leo.erp.system.generalsetting.web.dto.GeneralSettingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class GeneralSettingQueryService {

    private static final int DEFAULT_SORT_ORDER = 500;
    private static final Map<String, Integer> GENERAL_SETTING_ORDER = Map.ofEntries(
            Map.entry(SystemSwitchService.DEFAULT_LIST_PAGE_SIZE_SETTING, 98),
            Map.entry("UI_WEIGHT_ONLY_PURCHASE_INBOUNDS", 100),
            Map.entry("UI_WEIGHT_ONLY_SALES_OUTBOUNDS", 110),
            Map.entry(SystemSwitchService.CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER_SWITCH, 120),
            Map.entry("UI_HIDE_AUDITED_LIST_RECORDS", 190),
            Map.entry("UI_SHOW_SNOWFLAKE_ID", 200)
    );

    private final GeneralSettingRepository generalSettingRepository;
    private final GeneralSettingMapper generalSettingMapper;

    public GeneralSettingQueryService(GeneralSettingRepository generalSettingRepository,
                                      GeneralSettingMapper generalSettingMapper) {
        this.generalSettingRepository = generalSettingRepository;
        this.generalSettingMapper = generalSettingMapper;
    }

    @Transactional(readOnly = true)
    public Page<GeneralSettingResponse> page(PageQuery query, String keyword, String status) {
        Specification<GeneralSetting> spec = Specs
                .<GeneralSetting>notDeleted()
                .and(Specs.keywordLike(keyword, "settingCode", "settingName", "settingGroup"))
                .and(Specs.equalIfPresent("status", status));
        List<GeneralSettingResponse> merged = new ArrayList<>();
        generalSettingRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(generalSettingMapper::toResponse)
                .forEach(merged::add);
        merged.removeIf(item -> !matchesStatus(item, status));
        merged.sort(generalSettingComparator());

        int start = Math.min(query.page() * query.size(), merged.size());
        int end = Math.min(start + query.size(), merged.size());
        return new PageImpl<>(
                merged.subList(start, end),
                PageRequest.of(query.page(), query.size()),
                merged.size()
        );
    }

    private boolean matchesStatus(GeneralSettingResponse item, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return status.trim().equals(item.status());
    }

    private Comparator<GeneralSettingResponse> generalSettingComparator() {
        return Comparator
                .comparingInt(this::resolveSortOrder)
                .thenComparing(item -> defaultString(item.settingGroup()))
                .thenComparing(item -> defaultString(item.settingCode()));
    }

    private int resolveSortOrder(GeneralSettingResponse item) {
        return GENERAL_SETTING_ORDER.getOrDefault(item.settingCode(), DEFAULT_SORT_ORDER);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
