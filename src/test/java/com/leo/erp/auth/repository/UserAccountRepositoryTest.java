package com.leo.erp.auth.repository;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserAccountRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserAccountRepository repository;

    private long nextId = 1;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
        entityManager.flush();
    }

    private UserAccount createUser(String loginName, String userName, boolean deleted) {
        UserAccount user = new UserAccount();
        user.setId(nextId++);
        user.setLoginName(loginName);
        user.setPasswordHash("$2a$10$dummyHashForTesting");
        user.setUserName(userName);
        user.setStatus(UserStatus.NORMAL);
        user.setCreatedBy(0L);
        user.setCreatedAt(LocalDateTime.now());
        user.setDeletedFlag(deleted);
        return user;
    }

    @Test
    void findByLoginName_shouldReturnUserWhenExists() {
        UserAccount user = createUser("testuser", "测试用户", false);
        entityManager.persistAndFlush(user);

        Optional<UserAccount> result = repository.findByLoginName("testuser");

        assertThat(result).isPresent();
        assertThat(result.get().getUserName()).isEqualTo("测试用户");
    }

    @Test
    void findByLoginName_shouldReturnEmptyWhenNotExists() {
        Optional<UserAccount> result = repository.findByLoginName("nonexist");

        assertThat(result).isEmpty();
    }

    @Test
    void findByLoginNameAndDeletedFlagFalse_shouldReturnUserWhenExistsAndNotDeleted() {
        UserAccount user = createUser("testuser", "测试用户", false);
        entityManager.persistAndFlush(user);

        Optional<UserAccount> result = repository.findByLoginNameAndDeletedFlagFalse("testuser");

        assertThat(result).isPresent();
        assertThat(result.get().getUserName()).isEqualTo("测试用户");
    }

    @Test
    void findByLoginNameAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        UserAccount user = createUser("deleteduser", "已删除用户", true);
        entityManager.persistAndFlush(user);

        Optional<UserAccount> result = repository.findByLoginNameAndDeletedFlagFalse("deleteduser");

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnUserWhenExistsAndNotDeleted() {
        UserAccount user = createUser("testuser", "测试用户", false);
        entityManager.persistAndFlush(user);

        Optional<UserAccount> result = repository.findByIdAndDeletedFlagFalse(user.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getLoginName()).isEqualTo("testuser");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        UserAccount user = createUser("deleteduser", "已删除用户", true);
        entityManager.persistAndFlush(user);

        Optional<UserAccount> result = repository.findByIdAndDeletedFlagFalse(user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void existsByLoginName_shouldReturnTrueWhenExists() {
        UserAccount user = createUser("testuser", "测试用户", false);
        entityManager.persistAndFlush(user);

        boolean result = repository.existsByLoginName("testuser");

        assertThat(result).isTrue();
    }

    @Test
    void existsByLoginName_shouldReturnFalseWhenNotExists() {
        boolean result = repository.existsByLoginName("nonexist");

        assertThat(result).isFalse();
    }

    @Test
    void existsByLoginNameAndDeletedFlagFalse_shouldReturnTrueWhenExistsAndNotDeleted() {
        UserAccount user = createUser("testuser", "测试用户", false);
        entityManager.persistAndFlush(user);

        boolean result = repository.existsByLoginNameAndDeletedFlagFalse("testuser");

        assertThat(result).isTrue();
    }

    @Test
    void existsByLoginNameAndDeletedFlagFalse_shouldReturnFalseWhenDeleted() {
        UserAccount user = createUser("deleteduser", "已删除用户", true);
        entityManager.persistAndFlush(user);

        boolean result = repository.existsByLoginNameAndDeletedFlagFalse("deleteduser");

        assertThat(result).isFalse();
    }

    @Test
    void findByDepartmentIdAndDeletedFlagFalse_shouldReturnUsersInDepartment() {
        UserAccount user1 = createUser("user1", "用户1", false);
        user1.setDepartmentId(1L);
        entityManager.persist(user1);

        UserAccount user2 = createUser("user2", "用户2", false);
        user2.setDepartmentId(1L);
        entityManager.persist(user2);

        UserAccount user3 = createUser("user3", "用户3", false);
        user3.setDepartmentId(2L);
        entityManager.persist(user3);

        entityManager.flush();

        List<UserAccount> result = repository.findByDepartmentIdAndDeletedFlagFalse(1L);

        assertThat(result).hasSize(2);
    }

    @Test
    void countByDepartmentIdAndDeletedFlagFalse_shouldReturnCountInDepartment() {
        UserAccount user1 = createUser("user1", "用户1", false);
        user1.setDepartmentId(1L);
        entityManager.persist(user1);

        UserAccount user2 = createUser("user2", "用户2", false);
        user2.setDepartmentId(1L);
        entityManager.persist(user2);

        UserAccount deletedUser = createUser("deleteduser", "已删除用户", true);
        deletedUser.setDepartmentId(1L);
        entityManager.persist(deletedUser);

        entityManager.flush();

        long count = repository.countByDepartmentIdAndDeletedFlagFalse(1L);

        assertThat(count).isEqualTo(2);
    }
}
