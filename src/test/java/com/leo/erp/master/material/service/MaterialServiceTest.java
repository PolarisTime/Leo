package com.leo.erp.master.material.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.material.mapper.MaterialMapper;
import com.leo.erp.master.material.web.dto.MaterialImportResultResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaterialServiceTest {

    @Test
    void shouldImportCsvWithQuotedValuesAndBatchHeader() throws Exception {
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        TradeItemMaterialSupport tradeItemMaterialSupport = mock(TradeItemMaterialSupport.class);
        when(materialRepository.findByMaterialCode("MAT-001")).thenReturn(Optional.empty());
        when(materialRepository.save(any(Material.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeItemMaterialSupport.normalizeBatchNoEnabled(Boolean.TRUE)).thenReturn(true);

        MaterialService service = new MaterialService(
                materialRepository,
                new SnowflakeIdGenerator(1L),
                materialMapper,
                tradeItemMaterialSupport
        );

        String csv = "\uFEFF商品编码,品牌,材质,类别,规格,长度,单位,数量单位,件重(吨),每件支数,单价,批号管理,备注\r\n"
                + "MAT-001,宝钢,Q235B,板材,\"10,20\",6m,吨,支,1.234,12,500.50,是,\"含,逗号\"\r\n";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "materials.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        MaterialImportResultResponse result = service.importCsv(file);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isZero();
        assertThat(result.failedCount()).isZero();

        ArgumentCaptor<Material> materialCaptor = ArgumentCaptor.forClass(Material.class);
        verify(materialRepository).save(materialCaptor.capture());
        Material saved = materialCaptor.getValue();
        assertThat(saved.getMaterialCode()).isEqualTo("MAT-001");
        assertThat(saved.getSpec()).isEqualTo("10,20");
        assertThat(saved.getRemark()).isEqualTo("含,逗号");
        assertThat(saved.getBatchNoEnabled()).isTrue();
    }
}
