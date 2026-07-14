package com.leo.erp.system.company.repository;

import com.leo.erp.system.company.domain.entity.CompanySetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySettingRepositoryTest {

    @Mock
    private CompanySettingRepository repository;

    @Test
    void existsByDeletedFlagFalseShouldReturnTrueWhenExists() {
        when(repository.existsByDeletedFlagFalse()).thenReturn(true);

        boolean result = repository.existsByDeletedFlagFalse();

        assertThat(result).isTrue();
    }

    @Test
    void existsByDeletedFlagFalseShouldReturnFalseWhenEmpty() {
        when(repository.existsByDeletedFlagFalse()).thenReturn(false);

        boolean result = repository.existsByDeletedFlagFalse();

        assertThat(result).isFalse();
    }

    @Test
    void findFirstShouldReturnWhenExists() {
        CompanySetting setting = createSetting(1L, "公司A");
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.of(setting));

        Optional<CompanySetting> result = repository.findFirstByDeletedFlagFalseOrderByIdAsc();

        assertThat(result).isPresent();
        assertThat(result.get().getCompanyName()).isEqualTo("公司A");
    }

    @Test
    void findFirstShouldReturnEmptyWhenNone() {
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());

        Optional<CompanySetting> result = repository.findFirstByDeletedFlagFalseOrderByIdAsc();

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdShouldReturnWhenExists() {
        CompanySetting setting = createSetting(1L, "公司A");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(setting));

        Optional<CompanySetting> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
    }

    @Test
    void findByIdShouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        Optional<CompanySetting> result = repository.findByIdAndDeletedFlagFalse(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void existsByCompanyNameShouldReturnTrueWhenExists() {
        when(repository.existsByCompanyNameAndDeletedFlagFalse("公司A")).thenReturn(true);

        boolean result = repository.existsByCompanyNameAndDeletedFlagFalse("公司A");

        assertThat(result).isTrue();
    }

    @Test
    void existsByCompanyNameShouldReturnFalseWhenNotExists() {
        when(repository.existsByCompanyNameAndDeletedFlagFalse("不存在")).thenReturn(false);

        boolean result = repository.existsByCompanyNameAndDeletedFlagFalse("不存在");

        assertThat(result).isFalse();
    }

    private CompanySetting createSetting(Long id, String companyName) {
        CompanySetting setting = new CompanySetting();
        setting.setId(id);
        setting.setCompanyName(companyName);
        setting.setTaxNo("TAX001");
        setting.setStatus("正常");
        return setting;
    }
}
