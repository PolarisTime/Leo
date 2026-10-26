package com.leo.erp.master.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 「项目简称批量填充」唯一出口的口径测试。
 *
 * <p>锁定的口径：项目缺失 / 已删除 / 简称为空 → 留 null（不用全称兜底）；
 * 整页只发一次批量查询（禁止 N+1）；简称去首尾空白。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectNameAbbrResolverTest {

    @Mock
    private ProjectQuery projectQuery;

    /** 用最小记录代替真实响应 DTO，验证「批量查询 + 回填」本身，不掺入四条单据的字段噪音。 */
    private record Row(Long projectId, String projectName, String projectNameAbbr) {
        Row withAbbr(String abbreviatedName) {
            return new Row(projectId, projectName, abbreviatedName);
        }
    }

    // ---------- 列表：整页一次查询 ----------

    @Test
    void fillList_shouldQueryProjectsOnceForWholePageWithDistinctIds() {
        List<Row> rows = List.of(row(20L), row(21L), row(20L));
        when(projectQuery.findActiveByIds(anyCollection())).thenReturn(Map.of(
                20L, snapshot(20L, "项A"),
                21L, snapshot(21L, "项B")));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> idsCaptor = ArgumentCaptor.forClass(Collection.class);

        List<Row> filled = ProjectNameAbbrResolver.fill(rows, projectQuery, Row::projectId, Row::withAbbr);

        verify(projectQuery, times(1)).findActiveByIds(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(20L, 21L);
        assertThat(filled).extracting(Row::projectNameAbbr).containsExactly("项A", "项B", "项A");
    }

    @Test
    void fillList_shouldNotQueryAndKeepSameInstancesWhenPageHasNoProjectId() {
        List<Row> rows = List.of(row(null), row(null));

        List<Row> filled = ProjectNameAbbrResolver.fill(rows, projectQuery, Row::projectId, Row::withAbbr);

        assertThat(filled).isSameAs(rows);
        verifyNoInteractions(projectQuery);
    }

    @Test
    void fillList_shouldLeaveNullWhenProjectMissingOrDeleted() {
        List<Row> rows = List.of(row(20L), row(21L));
        when(projectQuery.findActiveByIds(anyCollection())).thenReturn(Map.of(20L, snapshot(20L, "项A")));

        List<Row> filled = ProjectNameAbbrResolver.fill(rows, projectQuery, Row::projectId, Row::withAbbr);

        // 已删除的项目不在批量结果里 → 该行 projectNameAbbr 留 null，不回退成项目全称
        assertThat(filled).extracting(Row::projectNameAbbr).containsExactly("项A", null);
        // 全称原样留在 projectName 上，不得被当成简称兜底
        assertThat(filled.get(1).projectName()).isEqualTo("项目21");
    }

    @Test
    void fillList_shouldReturnSameListWhenNoProjectResolved() {
        List<Row> rows = List.of(row(20L));
        when(projectQuery.findActiveByIds(anyCollection())).thenReturn(Map.of());

        assertThat(ProjectNameAbbrResolver.fill(rows, projectQuery, Row::projectId, Row::withAbbr)).isSameAs(rows);
    }

    @Test
    void fillList_shouldTreatBlankAbbreviatedNameAsAbsent() {
        List<Row> rows = List.of(row(20L));
        when(projectQuery.findActiveByIds(anyCollection())).thenReturn(Map.of(20L, snapshot(20L, "   ")));

        List<Row> filled = ProjectNameAbbrResolver.fill(rows, projectQuery, Row::projectId, Row::withAbbr);

        assertThat(filled.get(0).projectNameAbbr()).isNull();
    }

    @Test
    void fillList_shouldTrimAbbreviatedName() {
        List<Row> rows = List.of(row(20L));
        when(projectQuery.findActiveByIds(anyCollection())).thenReturn(Map.of(20L, snapshot(20L, " 项A ")));

        List<Row> filled = ProjectNameAbbrResolver.fill(rows, projectQuery, Row::projectId, Row::withAbbr);

        assertThat(filled.get(0).projectNameAbbr()).isEqualTo("项A");
    }

    @Test
    void fillList_shouldIgnoreNullSnapshot() {
        List<Row> rows = List.of(row(20L));
        Map<Long, ProjectQuery.ProjectSnapshot> snapshots = new HashMap<>();
        snapshots.put(20L, null);
        when(projectQuery.findActiveByIds(anyCollection())).thenReturn(snapshots);

        assertThat(ProjectNameAbbrResolver.fill(rows, projectQuery, Row::projectId, Row::withAbbr)).isSameAs(rows);
    }

    // ---------- 分页：保留分页元数据 ----------

    @Test
    void fillPage_shouldKeepPaginationMetadataAndQueryOnce() {
        Page<Row> page = new PageImpl<>(List.of(row(20L), row(21L)), PageRequest.of(2, 2), 7);
        when(projectQuery.findActiveByIds(anyCollection())).thenReturn(Map.of(20L, snapshot(20L, "项A")));

        Page<Row> filled = ProjectNameAbbrResolver.fill(page, projectQuery, Row::projectId, Row::withAbbr);

        assertThat(filled.getTotalElements()).isEqualTo(7);
        assertThat(filled.getNumber()).isEqualTo(2);
        assertThat(filled.getSize()).isEqualTo(2);
        assertThat(filled.getContent()).extracting(Row::projectNameAbbr).containsExactly("项A", null);
        verify(projectQuery, times(1)).findActiveByIds(anyCollection());
    }

    @Test
    void fillPage_shouldNotQueryForEmptyPage() {
        Page<Row> page = new PageImpl<>(List.of());

        assertThat(ProjectNameAbbrResolver.fill(page, projectQuery, Row::projectId, Row::withAbbr)).isSameAs(page);
        verifyNoInteractions(projectQuery);
    }

    // ---------- 详情：单条只查一次 ----------

    @Test
    void fillOne_shouldQueryOnlyTheGivenProject() {
        when(projectQuery.findActiveByIds(anyCollection())).thenReturn(Map.of(20L, snapshot(20L, "项A")));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> idsCaptor = ArgumentCaptor.forClass(Collection.class);

        Row filled = ProjectNameAbbrResolver.fillOne(row(20L), projectQuery, Row::projectId, Row::withAbbr);

        verify(projectQuery, times(1)).findActiveByIds(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(20L);
        assertThat(filled.projectNameAbbr()).isEqualTo("项A");
    }

    @Test
    void fillOne_shouldReturnSameRowWhenProjectMissing() {
        Row row = row(20L);
        when(projectQuery.findActiveByIds(anyCollection())).thenReturn(Map.of());

        assertThat(ProjectNameAbbrResolver.fillOne(row, projectQuery, Row::projectId, Row::withAbbr)).isSameAs(row);
    }

    @Test
    void fillOne_shouldReturnNullAndNotQueryForNullRow() {
        assertThat(ProjectNameAbbrResolver.fillOne(null, projectQuery, Row::projectId, Row::withAbbr)).isNull();

        verifyNoInteractions(projectQuery);
    }

    private static Row row(Long projectId) {
        return new Row(projectId, "项目" + projectId, null);
    }

    private static ProjectQuery.ProjectSnapshot snapshot(Long projectId, String abbreviatedName) {
        return new ProjectQuery.ProjectSnapshot(projectId, "项目" + projectId, abbreviatedName, 10L, "CUST001");
    }
}
