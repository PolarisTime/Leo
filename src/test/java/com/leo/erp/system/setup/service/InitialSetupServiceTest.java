package com.leo.erp.system.setup.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.auth.service.TotpService;
import com.leo.erp.auth.service.UserRoleBindingService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import com.leo.erp.system.setup.web.dto.InitialSetupAdminRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupAdminSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupCompanyRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupTotpSetupRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialSetupServiceTest {

    private UserAccountRepository userAccountRepository;
    private UserRoleRepository userRoleRepository;
    private UserRoleBindingService userRoleBindingService;
    private RoleSettingRepository roleSettingRepository;
    private CompanySettingRepository companySettingRepository;
    private NoRuleRepository noRuleRepository;
    private DepartmentRepository departmentRepository;
    private PasswordEncoder passwordEncoder;
    private TotpService totpService;
    private StringRedisTemplate redisTemplate;
    private InitialSetupService service;

    @BeforeEach
    void setUp() {
        userAccountRepository = (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByLoginNameAndDeletedFlagFalse" -> false;
                    case "saveAndFlush" -> {
                        var ua = (UserAccount) args[0];
                        ua.setId(1L);
                        yield ua;
                    }
                    case "toString" -> "UserAccountRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        userRoleRepository = (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "countActiveUsersByRoleId" -> 0L;
                    case "findFirstActiveUserByRoleId" -> {
                        var ua = new UserAccount();
                        ua.setLoginName("admin");
                        yield Optional.of(ua);
                    }
                    case "toString" -> "UserRoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        userRoleBindingService = mock(UserRoleBindingService.class);
        roleSettingRepository = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleCodeAndDeletedFlagFalse" -> {
                        var role = new RoleSetting();
                        role.setId(1L);
                        role.setRoleCode("ADMIN");
                        role.setRoleName("系统管理员");
                        yield Optional.of(role);
                    }
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        companySettingRepository = (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByDeletedFlagFalse" -> false;
                    case "saveAndFlush" -> {
                        var cs = (CompanySetting) args[0];
                        cs.setId(1L);
                        yield cs;
                    }
                    case "findFirstByDeletedFlagFalseOrderByIdAsc" -> Optional.empty();
                    case "toString" -> "CompanySettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        noRuleRepository = (NoRuleRepository) Proxy.newProxyInstance(
                NoRuleRepository.class.getClassLoader(),
                new Class[]{NoRuleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findBySettingCodeAndDeletedFlagFalse" -> Optional.empty();
                    case "save" -> args[0];
                    case "toString" -> "NoRuleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        departmentRepository = (DepartmentRepository) Proxy.newProxyInstance(
                DepartmentRepository.class.getClassLoader(),
                new Class[]{DepartmentRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDepartmentCodeAndDeletedFlagFalse" -> {
                        var dept = new Department();
                        dept.setId(1L);
                        dept.setDepartmentName("默认部门");
                        yield Optional.of(dept);
                    }
                    case "toString" -> "DepartmentRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        passwordEncoder = (PasswordEncoder) Proxy.newProxyInstance(
                PasswordEncoder.class.getClassLoader(),
                new Class[]{PasswordEncoder.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "encode" -> "encoded-password";
                    case "toString" -> "PasswordEncoderStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        totpService = mock(TotpService.class);
        when(totpService.generateSecret()).thenReturn("JBSWY3DPEHPK3PXP");
        when(totpService.generateQrCodeImage(anyString(), anyString())).thenReturn(new byte[100]);
        when(totpService.verifyCode(anyString(), anyString())).thenReturn(true);
        when(totpService.encryptSecret(anyString())).thenReturn("encrypted-secret");
        redisTemplate = redisTemplate();
        service = new InitialSetupService(
                userAccountRepository, userRoleRepository, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );
    }

    @Test
    void shouldReturnSetupStatus() {
        var status = service.status();
        assertThat(status).isNotNull();
        assertThat(status.setupRequired()).isTrue();
    }

    @Test
    void shouldNotRequireSetupWhenOobeCompletedSwitchExists() {
        var svc = new InitialSetupService(
                userAccountRepository, userRoleRepository, userRoleBindingService,
                roleSettingRepository, companySettingRepository, oobeCompletedNoRuleRepository(),
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        var status = svc.status();

        assertThat(status.setupRequired()).isFalse();
        assertThat(status.adminConfigured()).isFalse();
        assertThat(status.companyConfigured()).isFalse();
    }

    @Test
    void shouldRejectSetupAdminTotpWhenOobeCompletedSwitchExists() {
        var svc = new InitialSetupService(
                userAccountRepository, userRoleRepository, userRoleBindingService,
                roleSettingRepository, companySettingRepository, oobeCompletedNoRuleRepository(),
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        assertThatThrownBy(() -> svc.setupAdminTotp(new InitialSetupTotpSetupRequest("admin")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统已完成初始化");
    }

    @Test
    void shouldThrowException_whenOobeCompleted() {
        var repo = (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByDeletedFlagFalse" -> true;
                    case "toString" -> "CompanySettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var urRepo = (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "countActiveUsersByRoleId" -> 1L;
                    case "toString" -> "UserRoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, repo, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        assertThatThrownBy(() -> svc.initialize(new InitialSetupSubmitRequest(null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统已完成初始化");
    }

    @Test
    void shouldRejectInitializeWhenSetupBecomesCompletedAfterFirstCheck() {
        var urRepo = mock(UserRoleRepository.class);
        when(urRepo.countActiveUsersByRoleId(1L)).thenReturn(0L, 1L);
        var companyRepo = mock(CompanySettingRepository.class);
        when(companyRepo.existsByDeletedFlagFalse()).thenReturn(true);
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companyRepo, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        assertThatThrownBy(() -> svc.initialize(new InitialSetupSubmitRequest(null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统已完成首次初始化");
    }

    @Test
    void shouldThrowException_whenSetupAdminTotpAfterAdminConfigured() {
        var repo = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleCodeAndDeletedFlagFalse" -> {
                        var role = new RoleSetting();
                        role.setId(1L);
                        role.setRoleCode("ADMIN");
                        yield Optional.of(role);
                    }
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var urRepo = (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "countActiveUsersByRoleId" -> 1L;
                    case "toString" -> "UserRoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                repo, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        assertThatThrownBy(() -> svc.setupAdminTotp(new InitialSetupTotpSetupRequest("admin")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员账号已完成初始化");
    }

    @Test
    void shouldReturnTotpSetup_whenSetupAdminTotp() {
        var result = service.setupAdminTotp(new InitialSetupTotpSetupRequest("admin"));
        assertThat(result).isNotNull();
        assertThat(result.secret()).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void shouldRejectConfigureAdminWhenServerSideTotpSecretMissing() {
        assertThatThrownBy(() -> service.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "12345678", "管理员", "13800138000"),
                "client-secret", "123456"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先生成并绑定管理员 2FA");
    }

    @Test
    void shouldThrowException_whenSetupAdminTotpWithNullLoginName() {
        assertThatThrownBy(() -> service.setupAdminTotp(new InitialSetupTotpSetupRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员登录账号不能为空");
    }

    @Test
    void shouldThrowException_whenSetupAdminTotpRequestMissing() {
        assertThatThrownBy(() -> service.setupAdminTotp(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员登录账号不能为空");
    }

    @Test
    void shouldThrowException_whenConfigureAdminAfterAlreadyConfigured() {
        var repo = (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleCodeAndDeletedFlagFalse" -> {
                        var role = new RoleSetting();
                        role.setId(1L);
                        role.setRoleCode("ADMIN");
                        yield Optional.of(role);
                    }
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var urRepo = (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "countActiveUsersByRoleId" -> 1L;
                    case "toString" -> "UserRoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                repo, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        assertThatThrownBy(() -> svc.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "12345678", "管理员", "13800138000"), "secret", "123456"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员账号已完成初始化");
    }

    @Test
    void shouldRejectConfigureAdminWhenAdminBecomesConfiguredDuringRequest() {
        var urRepo = mock(UserRoleRepository.class);
        when(urRepo.countActiveUsersByRoleId(1L)).thenReturn(0L, 0L, 1L);
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        assertThatThrownBy(() -> svc.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "12345678", "管理员", "13800138000"), "secret", "123456"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员账号已完成初始化");
    }

    @Test
    void shouldThrowException_whenConfigureCompanyBeforeAdmin() {
        assertThatThrownBy(() -> service.configureCompany(new InitialSetupCompanyRequest(
                "公司A", "税号", "银行", "账号", new BigDecimal("0.13"), ""
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先完成管理员账号初始化");
    }

    @Test
    void shouldInitializeSuccessfully_whenNoAdminAndNoCompany() {
        service.setupAdminTotp(new InitialSetupTotpSetupRequest("admin"));
        var result = service.initialize(new InitialSetupSubmitRequest(
                new InitialSetupAdminSubmitRequest(
                        new InitialSetupAdminRequest("admin", "12345678", "管理员", "13800138000"),
                        "JBSWY3DPEHPK3PXP", "123456"
                ),
                new InitialSetupCompanyRequest("公司A", "税号", "银行", "账号", new BigDecimal("0.13"), "")
        ));
        assertThat(result).isNotNull();
        assertThat(result.adminLoginName()).isEqualTo("admin");
        assertThat(result.companyName()).isEqualTo("公司A");
    }

    @Test
    void shouldRejectInitializeWhenRequestMissingAndAdminRequired() {
        assertThatThrownBy(() -> service.initialize(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请填写管理员账号信息");
    }

    @Test
    void shouldConfigureAdminSuccessfully() {
        service.setupAdminTotp(new InitialSetupTotpSetupRequest("admin"));
        var result = service.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "12345678", "管理员", "13800138000"),
                "JBSWY3DPEHPK3PXP", "123456"
        ));
        assertThat(result).isNotNull();
        assertThat(result.adminLoginName()).isEqualTo("admin");
        assertThat(result.companyName()).isNull();
        verify(redisTemplate).delete("setup:admin:totp:admin");
    }

    @Test
    void shouldUseServerSideTotpSecretWhenConfigureAdmin() {
        service.setupAdminTotp(new InitialSetupTotpSetupRequest("admin"));

        service.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "12345678", "管理员", "13800138000"),
                "attacker-secret", "123456"
        ));

        org.mockito.Mockito.verify(totpService).verifyCode("JBSWY3DPEHPK3PXP", "123456");
        org.mockito.Mockito.verify(totpService).encryptSecret("JBSWY3DPEHPK3PXP");
        verify(redisTemplate).delete("setup:admin:totp:admin");
    }

    @Test
    void shouldConfigureCompanySuccessfully_whenAdminConfigured() {
        var urRepo = (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "countActiveUsersByRoleId" -> 1L;
                    case "findFirstActiveUserByRoleId" -> {
                        var ua = new UserAccount();
                        ua.setLoginName("admin");
                        yield Optional.of(ua);
                    }
                    case "toString" -> "UserRoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        var result = svc.configureCompany(new InitialSetupCompanyRequest(
                "公司A", "税号", "银行", "账号", new BigDecimal("0.13"), ""
        ));
        assertThat(result).isNotNull();
        assertThat(result.adminLoginName()).isEqualTo("admin");
        assertThat(result.companyName()).isEqualTo("公司A");
    }

    @Test
    void shouldConfigureCompanyWithRemark() {
        var urRepo = (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "countActiveUsersByRoleId" -> 1L;
                    case "findFirstActiveUserByRoleId" -> {
                        var ua = new UserAccount();
                        ua.setLoginName("admin");
                        yield Optional.of(ua);
                    }
                    case "toString" -> "UserRoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        var result = svc.configureCompany(new InitialSetupCompanyRequest(
                "公司A", "税号", "银行", "账号", new BigDecimal("0.13"), "备注"
        ));
        assertThat(result).isNotNull();
        assertThat(result.companyName()).isEqualTo("公司A");
    }

    @Test
    void shouldConfigureCompanyWithoutRemark() {
        var urRepo = (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "countActiveUsersByRoleId" -> 1L;
                    case "findFirstActiveUserByRoleId" -> {
                        var ua = new UserAccount();
                        ua.setLoginName("admin");
                        yield Optional.of(ua);
                    }
                    case "toString" -> "UserRoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        var result = svc.configureCompany(new InitialSetupCompanyRequest(
                "公司A", "税号", "银行", "账号", new BigDecimal("0.13"), null
        ));
        assertThat(result).isNotNull();
        assertThat(result.companyName()).isEqualTo("公司A");
    }

    @Test
    void shouldInitializeCompanyAndCompleteOobeWhenAdminAlreadyConfigured() {
        var urRepo = mock(UserRoleRepository.class);
        when(urRepo.countActiveUsersByRoleId(1L)).thenReturn(1L);
        var existingAdmin = new UserAccount();
        existingAdmin.setLoginName("admin");
        when(urRepo.findFirstActiveUserByRoleId(1L)).thenReturn(Optional.of(existingAdmin));
        var companyRepo = mock(CompanySettingRepository.class);
        when(companyRepo.existsByDeletedFlagFalse()).thenReturn(false, false, true);
        when(companyRepo.saveAndFlush(any(CompanySetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var noRuleRepo = mock(NoRuleRepository.class);
        when(noRuleRepo.findBySettingCodeAndDeletedFlagFalse(anyString())).thenReturn(Optional.empty());
        when(noRuleRepo.save(any(NoRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companyRepo, noRuleRepo,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        var result = svc.initialize(new InitialSetupSubmitRequest(
                null,
                new InitialSetupCompanyRequest("公司A", "税号", "银行", "账号", new BigDecimal("0.13"), "")
        ));

        assertThat(result.adminLoginName()).isEqualTo("admin");
        assertThat(result.companyName()).isEqualTo("公司A");
        var captor = org.mockito.ArgumentCaptor.forClass(NoRule.class);
        verify(noRuleRepo, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(setting -> {
            assertThat(setting.getSettingCode()).isEqualTo("SYS_OOBE_COMPLETED");
            assertThat(setting.getStatus()).isEqualTo("正常");
        });
    }

    @Test
    void shouldRejectInitializeWhenRequestMissingAndCompanyRequired() {
        var urRepo = mock(UserRoleRepository.class);
        when(urRepo.countActiveUsersByRoleId(1L)).thenReturn(1L);
        var existingAdmin = new UserAccount();
        existingAdmin.setLoginName("admin");
        when(urRepo.findFirstActiveUserByRoleId(1L)).thenReturn(Optional.of(existingAdmin));
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        assertThatThrownBy(() -> svc.initialize(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请填写公司主体信息");
    }

    @Test
    void shouldNotCompleteOobeWhenCompanyStillNotVisibleAfterInitialize() {
        var urRepo = mock(UserRoleRepository.class);
        when(urRepo.countActiveUsersByRoleId(1L)).thenReturn(1L);
        var existingAdmin = new UserAccount();
        existingAdmin.setLoginName("admin");
        when(urRepo.findFirstActiveUserByRoleId(1L)).thenReturn(Optional.of(existingAdmin));
        var companyRepo = mock(CompanySettingRepository.class);
        when(companyRepo.existsByDeletedFlagFalse()).thenReturn(false, false, false);
        when(companyRepo.saveAndFlush(any(CompanySetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var noRuleRepo = mock(NoRuleRepository.class);
        when(noRuleRepo.findBySettingCodeAndDeletedFlagFalse(anyString())).thenReturn(Optional.empty());
        when(noRuleRepo.save(any(NoRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companyRepo, noRuleRepo,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        var result = svc.initialize(new InitialSetupSubmitRequest(
                null,
                new InitialSetupCompanyRequest("公司A", "税号", "银行", "账号", new BigDecimal("0.13"), "")
        ));

        assertThat(result.companyName()).isEqualTo("公司A");
        var captor = org.mockito.ArgumentCaptor.forClass(NoRule.class);
        verify(noRuleRepo, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .noneMatch(setting -> "SYS_OOBE_COMPLETED".equals(setting.getSettingCode()));
    }

    @Test
    void shouldReuseExistingOobeCompletedSwitchWhenCompletingInitialize() {
        var urRepo = mock(UserRoleRepository.class);
        when(urRepo.countActiveUsersByRoleId(1L)).thenReturn(1L);
        var existingAdmin = new UserAccount();
        existingAdmin.setLoginName("admin");
        when(urRepo.findFirstActiveUserByRoleId(1L)).thenReturn(Optional.of(existingAdmin));
        var companyRepo = mock(CompanySettingRepository.class);
        when(companyRepo.existsByDeletedFlagFalse()).thenReturn(false, false, true);
        when(companyRepo.saveAndFlush(any(CompanySetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var existingOobeSwitch = new NoRule();
        existingOobeSwitch.setId(99L);
        existingOobeSwitch.setSettingCode("SYS_OOBE_COMPLETED");
        existingOobeSwitch.setSettingName("旧开关");
        existingOobeSwitch.setStatus("停用");
        var noRuleRepo = mock(NoRuleRepository.class);
        when(noRuleRepo.findBySettingCodeAndDeletedFlagFalse(anyString())).thenAnswer(invocation -> {
            if ("SYS_OOBE_COMPLETED".equals(invocation.getArgument(0))) {
                return Optional.of(existingOobeSwitch);
            }
            return Optional.empty();
        });
        when(noRuleRepo.save(any(NoRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companyRepo, noRuleRepo,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        svc.initialize(new InitialSetupSubmitRequest(
                null,
                new InitialSetupCompanyRequest("公司A", "税号", "银行", "账号", new BigDecimal("0.13"), "")
        ));

        var captor = org.mockito.ArgumentCaptor.forClass(NoRule.class);
        verify(noRuleRepo, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(setting -> {
            assertThat(setting.getId()).isEqualTo(99L);
            assertThat(setting.getSettingCode()).isEqualTo("SYS_OOBE_COMPLETED");
            assertThat(setting.getSampleNo()).isEqualTo("COMPLETED");
            assertThat(setting.getStatus()).isEqualTo("正常");
            assertThat(setting.getSettingName()).isEqualTo("旧开关");
        });
    }

    @Test
    void shouldInitializeAdminWhenCompanyAlreadyConfigured() {
        var companyRepo = mock(CompanySettingRepository.class);
        when(companyRepo.existsByDeletedFlagFalse()).thenReturn(true);
        var company = new CompanySetting();
        company.setCompanyName("公司A");
        when(companyRepo.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.of(company));
        var svc = new InitialSetupService(
                userAccountRepository, userRoleRepository, userRoleBindingService,
                roleSettingRepository, companyRepo, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );
        svc.setupAdminTotp(new InitialSetupTotpSetupRequest("admin"));

        var result = svc.initialize(new InitialSetupSubmitRequest(
                new InitialSetupAdminSubmitRequest(
                        new InitialSetupAdminRequest("admin", "12345678", "管理员", null),
                        "ignored", "123456"
                ),
                null
        ));

        assertThat(result.adminLoginName()).isEqualTo("admin");
        assertThat(result.companyName()).isEqualTo("公司A");
    }

    @Test
    void shouldCreateAdminWithoutDepartmentWhenDefaultDepartmentMissing() {
        var userRepo = mock(UserAccountRepository.class);
        when(userRepo.existsByLoginNameAndDeletedFlagFalse("admin")).thenReturn(false);
        when(userRepo.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var deptRepo = mock(DepartmentRepository.class);
        when(deptRepo.findByDepartmentCodeAndDeletedFlagFalse("DEPT001")).thenReturn(Optional.empty());
        var svc = new InitialSetupService(
                userRepo, userRoleRepository, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                deptRepo, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );
        svc.setupAdminTotp(new InitialSetupTotpSetupRequest("admin"));

        var result = svc.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "12345678", "管理员", null),
                null, "123456"
        ));

        assertThat(result.adminLoginName()).isEqualTo("admin");
        var captor = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepo).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getDepartmentId()).isNull();
        assertThat(captor.getValue().getDepartmentName()).isNull();
    }

    @Test
    void shouldRejectConfigureCompanyWhenCompanyAlreadyConfigured() {
        var urRepo = mock(UserRoleRepository.class);
        when(urRepo.countActiveUsersByRoleId(1L)).thenReturn(1L);
        var companyRepo = mock(CompanySettingRepository.class);
        when(companyRepo.existsByDeletedFlagFalse()).thenReturn(false, true);
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companyRepo, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        assertThatThrownBy(() -> svc.configureCompany(new InitialSetupCompanyRequest(
                "公司A", "税号", "银行", "账号", new BigDecimal("0.13"), ""
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("默认结算主体已完成初始化");
    }

    @Test
    void shouldRejectConfigureAdminWhenRequestMissing() {
        assertThatThrownBy(() -> service.configureAdmin(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请填写管理员账号信息");
    }

    @Test
    void shouldRejectConfigureAdminWhenAdminPayloadMissing() {
        assertThatThrownBy(() -> service.configureAdmin(new InitialSetupAdminSubmitRequest(null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请填写管理员账号信息");
    }

    @Test
    void shouldRejectConfigureAdminWhenPasswordTooShort() {
        assertThatThrownBy(() -> service.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "1234567", "管理员", null),
                null, "123456"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员密码至少8位");
    }

    @Test
    void shouldRejectConfigureAdminWhenTotpCodeInvalid() {
        service.setupAdminTotp(new InitialSetupTotpSetupRequest("admin"));
        when(totpService.verifyCode(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "12345678", "管理员", null),
                null, "123456"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员 2FA 验证码不正确");
    }

    @Test
    void shouldRejectConfigureAdminWhenLoginNameExists() {
        var userRepo = mock(UserAccountRepository.class);
        when(userRepo.existsByLoginNameAndDeletedFlagFalse("admin")).thenReturn(true);
        var svc = new InitialSetupService(
                userRepo, userRoleRepository, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );
        svc.setupAdminTotp(new InitialSetupTotpSetupRequest("admin"));

        assertThatThrownBy(() -> svc.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "12345678", "管理员", null),
                null, "123456"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员登录账号已存在");
    }

    @Test
    void shouldRejectConfigureAdminWhenAdminRoleMissing() {
        var roleRepo = mock(RoleSettingRepository.class);
        when(roleRepo.findByRoleCodeAndDeletedFlagFalse("ADMIN")).thenReturn(Optional.empty());
        var svc = new InitialSetupService(
                userAccountRepository, userRoleRepository, userRoleBindingService,
                roleRepo, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );
        svc.setupAdminTotp(new InitialSetupTotpSetupRequest("admin"));

        assertThatThrownBy(() -> svc.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "12345678", "管理员", null),
                null, "123456"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到系统管理员角色");
    }

    @Test
    void shouldWrapTotpEncryptKeyMisconfiguration() {
        service.setupAdminTotp(new InitialSetupTotpSetupRequest("admin"));
        when(totpService.encryptSecret(anyString())).thenThrow(new IllegalStateException("missing key"));

        assertThatThrownBy(() -> service.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "12345678", "管理员", null),
                null, "123456"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("TOTP 加密密钥未配置");
    }

    @Test
    void shouldWrapDuplicateAdminDataIntegrityViolation() {
        var userRepo = mock(UserAccountRepository.class);
        when(userRepo.existsByLoginNameAndDeletedFlagFalse("admin")).thenReturn(false);
        when(userRepo.saveAndFlush(any(UserAccount.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        var svc = new InitialSetupService(
                userRepo, userRoleRepository, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );
        svc.setupAdminTotp(new InitialSetupTotpSetupRequest("admin"));

        assertThatThrownBy(() -> svc.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "12345678", "管理员", null),
                null, "123456"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员登录账号已存在");
    }

    @Test
    void shouldRejectCompanySetupWhenRequestMissing() {
        var urRepo = mock(UserRoleRepository.class);
        when(urRepo.countActiveUsersByRoleId(1L)).thenReturn(1L);
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        assertThatThrownBy(() -> svc.initialize(new InitialSetupSubmitRequest(null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请填写公司主体信息");
    }

    @Test
    void shouldRejectCompanySetupWhenTaxRateMissing() {
        var urRepo = mock(UserRoleRepository.class);
        when(urRepo.countActiveUsersByRoleId(1L)).thenReturn(1L);
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        assertThatThrownBy(() -> svc.configureCompany(new InitialSetupCompanyRequest(
                "公司A", "税号", "银行", "账号", null, ""
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("税率不能为空");
    }

    @Test
    void shouldReuseExistingDefaultTaxRateSettingWhenCompanyConfigured() {
        var urRepo = mock(UserRoleRepository.class);
        when(urRepo.countActiveUsersByRoleId(1L)).thenReturn(1L);
        var existingAdmin = new UserAccount();
        existingAdmin.setLoginName("admin");
        when(urRepo.findFirstActiveUserByRoleId(1L)).thenReturn(Optional.of(existingAdmin));
        var existingTaxRateSetting = new NoRule();
        existingTaxRateSetting.setId(88L);
        existingTaxRateSetting.setSettingCode("SYS_DEFAULT_TAX_RATE");
        existingTaxRateSetting.setSettingName("旧默认税率");
        var noRuleRepo = mock(NoRuleRepository.class);
        when(noRuleRepo.findBySettingCodeAndDeletedFlagFalse(anyString())).thenAnswer(invocation -> {
            if ("SYS_DEFAULT_TAX_RATE".equals(invocation.getArgument(0))) {
                return Optional.of(existingTaxRateSetting);
            }
            return Optional.empty();
        });
        when(noRuleRepo.save(any(NoRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepo,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        var result = svc.configureCompany(new InitialSetupCompanyRequest(
                "公司A", "税号", "银行", "账号", new BigDecimal("0.13"), ""
        ));

        assertThat(result.companyName()).isEqualTo("公司A");
        var captor = org.mockito.ArgumentCaptor.forClass(NoRule.class);
        verify(noRuleRepo).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(88L);
        assertThat(captor.getValue().getSettingName()).isEqualTo("旧默认税率");
        assertThat(captor.getValue().getSampleNo()).isEqualTo("0.1300");
        assertThat(captor.getValue().getStatus()).isEqualTo("正常");
    }

    @Test
    void shouldWrapSettlementAccountSerializationFailure() throws JsonProcessingException {
        var urRepo = mock(UserRoleRepository.class);
        when(urRepo.countActiveUsersByRoleId(1L)).thenReturn(1L);
        var objectMapper = mock(ObjectMapper.class);
        var serializationFailure = com.fasterxml.jackson.databind.JsonMappingException
                .fromUnexpectedIOE(new java.io.IOException("boom"));
        when(objectMapper.writeValueAsString(any())).thenThrow(serializationFailure);
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                objectMapper, totpService, redisTemplate
        );

        assertThatThrownBy(() -> svc.configureCompany(new InitialSetupCompanyRequest(
                "公司A", "税号", "银行", "账号", new BigDecimal("0.13"), ""
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("首次初始化结算账户序列化失败");
    }

    @Test
    void shouldWrapDuplicateCompanyDataIntegrityViolation() {
        var urRepo = mock(UserRoleRepository.class);
        when(urRepo.countActiveUsersByRoleId(1L)).thenReturn(1L);
        var companyRepo = mock(CompanySettingRepository.class);
        when(companyRepo.existsByDeletedFlagFalse()).thenReturn(false);
        when(companyRepo.saveAndFlush(any(CompanySetting.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        var svc = new InitialSetupService(
                userAccountRepository, urRepo, userRoleBindingService,
                roleSettingRepository, companyRepo, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplate
        );

        assertThatThrownBy(() -> svc.configureCompany(new InitialSetupCompanyRequest(
                "公司A", "税号", "银行", "账号", new BigDecimal("0.13"), ""
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("默认结算主体已存在");
    }

    @Test
    void shouldAllowTotpSetupWithoutRedisTemplateButRejectAdminSubmitUntilSecretCached() {
        var svc = new InitialSetupService(
                userAccountRepository, userRoleRepository, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, null
        );

        var setup = svc.setupAdminTotp(new InitialSetupTotpSetupRequest("admin"));

        assertThat(setup.secret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThatThrownBy(() -> svc.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "12345678", "管理员", null),
                null, "123456"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先生成并绑定管理员 2FA");
    }

    @Test
    void shouldRejectConfigureAdminWhenCachedTotpSecretIsBlank() {
        var svc = new InitialSetupService(
                userAccountRepository, userRoleRepository, userRoleBindingService,
                roleSettingRepository, companySettingRepository, noRuleRepository,
                departmentRepository, passwordEncoder, new SnowflakeIdGenerator(1),
                new ObjectMapper(), totpService, redisTemplateReturning(" ")
        );

        assertThatThrownBy(() -> svc.configureAdmin(new InitialSetupAdminSubmitRequest(
                new InitialSetupAdminRequest("admin", "12345678", "管理员", null),
                null, "123456"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先生成并绑定管理员 2FA");
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate redisTemplate() {
        Map<String, String> values = new HashMap<>();
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));
        when(valueOperations.get(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(invocation -> values.remove(invocation.getArgument(0)) != null);
        return redis;
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate redisTemplateReturning(String secret) {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(secret);
        return redis;
    }

    private NoRuleRepository oobeCompletedNoRuleRepository() {
        return (NoRuleRepository) Proxy.newProxyInstance(
                NoRuleRepository.class.getClassLoader(),
                new Class[]{NoRuleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findBySettingCodeAndDeletedFlagFalse" -> {
                        if ("SYS_OOBE_COMPLETED".equals(args[0])) {
                            var rule = new NoRule();
                            rule.setId(1L);
                            rule.setSettingCode("SYS_OOBE_COMPLETED");
                            rule.setStatus("正常");
                            rule.setSampleNo("COMPLETED");
                            yield Optional.of(rule);
                        }
                        yield Optional.empty();
                    }
                    case "save" -> args[0];
                    case "toString" -> "OobeCompletedNoRuleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
