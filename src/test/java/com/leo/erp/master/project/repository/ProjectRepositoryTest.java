package com.leo.erp.master.project.repository;

import com.leo.erp.master.project.domain.entity.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectRepositoryTest {

    @Mock
    private ProjectRepository repository;

    @Test
    void existsByProjectCodeAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByProjectCodeAndDeletedFlagFalse("P001")).thenReturn(true);

        boolean result = repository.existsByProjectCodeAndDeletedFlagFalse("P001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByProjectCodeAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsByProjectCodeAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByProjectCodeAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void existsByProjectCodeAndDeletedFlagFalse_shouldReturnFalseWhenDeleted() {
        when(repository.existsByProjectCodeAndDeletedFlagFalse("P002")).thenReturn(false);

        boolean result = repository.existsByProjectCodeAndDeletedFlagFalse("P002");

        assertThat(result).isFalse();
    }

    @Test
    void findByDeletedFlagFalseOrderByProjectCodeAsc_shouldReturnNonDeletedProjects() {
        Project project1 = new Project();
        project1.setProjectCode("P001");
        Project project2 = new Project();
        project2.setProjectCode("P002");
        when(repository.findByDeletedFlagFalseOrderByProjectCodeAsc())
                .thenReturn(List.of(project1, project2));

        List<Project> result = repository.findByDeletedFlagFalseOrderByProjectCodeAsc();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProjectCode()).isEqualTo("P001");
        assertThat(result.get(1).getProjectCode()).isEqualTo("P002");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnProjectWhenExistsAndNotDeleted() {
        Project project = new Project();
        project.setProjectCode("P001");
        project.setProjectName("测试项目");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(project));

        Optional<Project> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getProjectCode()).isEqualTo("P001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<Project> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void countByDeletedFlagFalse_shouldReturnCountOfNonDeletedProjects() {
        when(repository.countByDeletedFlagFalse()).thenReturn(2L);

        long count = repository.countByDeletedFlagFalse();

        assertThat(count).isEqualTo(2);
    }
}