package com.leo.erp.system.operationlog.repository;

import com.leo.erp.system.operationlog.domain.entity.OperationLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationLogRepositoryTest {

    @Mock
    private OperationLogRepository repository;

    @Test
    void findByIdShouldReturnOperationLogWhenExists() {
        OperationLog log = createLog(1L, "OP1");
        when(repository.findById(1L)).thenReturn(Optional.of(log));

        Optional<OperationLog> result = repository.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getLogNo()).isEqualTo("OP1");
    }

    @Test
    void findByIdShouldReturnEmptyWhenNotExists() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        Optional<OperationLog> result = repository.findById(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void saveShouldPersistOperationLog() {
        OperationLog log = createLog(1L, "OP1");
        when(repository.save(log)).thenReturn(log);

        OperationLog result = repository.save(log);

        assertThat(result).isNotNull();
        assertThat(result.getLogNo()).isEqualTo("OP1");
    }

    @Test
    void findAllShouldReturnAllLogs() {
        List<OperationLog> logs = List.of(createLog(1L, "OP1"), createLog(2L, "OP2"));
        when(repository.findAll()).thenReturn(logs);

        List<OperationLog> result = repository.findAll();

        assertThat(result).hasSize(2);
    }

    private OperationLog createLog(Long id, String logNo) {
        OperationLog log = new OperationLog();
        log.setId(id);
        log.setLogNo(logNo);
        log.setOperatorId(1L);
        log.setOperatorName("管理员");
        log.setLoginName("admin");
        log.setModuleName("用户管理");
        log.setActionType("创建");
        log.setRequestMethod("POST");
        log.setRequestPath("/api/users");
        log.setResultStatus("成功");
        log.setOperationTime(LocalDateTime.now());
        return log;
    }
}
