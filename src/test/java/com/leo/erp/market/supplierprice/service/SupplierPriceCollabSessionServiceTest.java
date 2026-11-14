package com.leo.erp.market.supplierprice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.api.AccountQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.supplierprice.CollabSessionProperties;
import com.leo.erp.market.supplierprice.domain.entity.SupplierPriceList;
import com.leo.erp.market.supplierprice.repository.SupplierPriceListRepository;
import com.leo.erp.market.supplierprice.web.dto.CollabSessionResponse;
import com.leo.erp.security.permission.PermissionChecker;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.support.SecurityPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 协作会话签发口径：房间按 listId（一张价格表一个房间）、writable 按 update 权限、
 * 名字与颜色稳定、404 语义。
 *
 * <p>票据本身不在这里断言内容（那是 {@link CollabTokenServiceTest} 的事），
 * 这里只验证「生成的票据确实能解回我们承诺的身份」，避免两边各说各话。</p>
 */
@ExtendWith(MockitoExtension.class)
class SupplierPriceCollabSessionServiceTest {

    private static final long LIST_ID = 900000000000000201L;
    private static final long SUPPLIER_ID = 333229108731322368L;
    private static final long USER_ID = 900000000000000202L;

    @Mock
    private SupplierPriceListRepository repository;

    @Mock
    private PermissionChecker permissionChecker;

    @Mock
    private AccountQuery accountQuery;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CollabSessionProperties properties = properties();

    @Test
    void createSession_shouldUseListIdAsRoom() {
        givenList();
        givenUpdatePermission(true);
        givenDisplayName("张三");

        CollabSessionResponse response = service()
                .createSession(LIST_ID, new SecurityPrincipal(USER_ID, "zhangsan", "", true, 0L));

        // 一张价格表一个房间：前缀 + listId，前端直接用这个串连接，不自己拼房间名。
        assertThat(response.room()).isEqualTo("supplier-price-list:" + LIST_ID);
        // 同一供应商可能出现在多个品牌列（多张表），房间不能退化成按供应商聚合。
        assertThat(response.room()).doesNotContain("supplier-price:" + SUPPLIER_ID);
        assertThat(response.url()).isEqualTo("ws://127.0.0.1:11212/collab");
    }

    @Test
    void createSession_withUpdatePermission_shouldBeWritableAndTokenCarriesIt() {
        givenList();
        givenUpdatePermission(true);
        givenDisplayName("张三");
        SecurityPrincipal principal = new SecurityPrincipal(USER_ID, "zhangsan", "", true, 0L);

        CollabSessionResponse response = service().createSession(LIST_ID, principal);
        CollabTokenClaims claims = tokenService().verify(response.token(), response.room());

        assertThat(response.writable()).isTrue();
        assertThat(claims.userId()).isEqualTo(String.valueOf(USER_ID));
        assertThat(claims.room()).isEqualTo("supplier-price-list:" + LIST_ID);
        assertThat(claims.writable()).isTrue();
        assertThat(claims.name()).isEqualTo("张三");
        assertThat(claims.color()).isEqualTo(response.color());
    }

    @Test
    void createSession_withoutUpdatePermission_shouldBeReadOnlyButStillIssueToken() {
        givenList();
        givenUpdatePermission(false);
        givenDisplayName("李四");
        SecurityPrincipal principal = new SecurityPrincipal(USER_ID, "lisi", "", true, 0L);

        CollabSessionResponse response = service().createSession(LIST_ID, principal);
        CollabTokenClaims claims = tokenService().verify(response.token(), response.room());

        assertThat(response.writable()).isFalse();
        assertThat(claims.writable()).isFalse();
        // 只读也要能连：票据照发，只是边车会丢弃该连接的 update。
        assertThat(response.token()).isNotBlank();
    }

    @Test
    void createSession_whenListMissing_shouldThrowNotFound() {
        when(repository.findByIdAndDeletedFlagFalse(LIST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service()
                .createSession(LIST_ID, new SecurityPrincipal(USER_ID, "zhangsan", "", true, 0L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void createSession_whenAccountHasNoDisplayName_shouldFallBackToLoginName() {
        givenList();
        givenUpdatePermission(true);
        when(accountQuery.findById(USER_ID)).thenReturn(Optional.of(
                new AccountQuery.AccountSnapshot(USER_ID, "zhangsan", "  ", null)));
        SecurityPrincipal principal = new SecurityPrincipal(USER_ID, "zhangsan", "", true, 0L);

        CollabSessionResponse response = service().createSession(LIST_ID, principal);
        CollabTokenClaims claims = tokenService().verify(response.token(), response.room());

        assertThat(claims.name()).isEqualTo("zhangsan");
    }

    @Test
    void createSession_colorShouldBeHexAndStableForSameUser() {
        givenList();
        givenUpdatePermission(true);
        givenDisplayName("张三");
        SecurityPrincipal principal = new SecurityPrincipal(USER_ID, "zhangsan", "", true, 0L);

        String first = service().createSession(LIST_ID, principal).color();
        String second = service().createSession(LIST_ID, principal).color();

        assertThat(first).matches("^#[0-9a-f]{6}$");
        assertThat(second).isEqualTo(first);
        assertThat(first).isEqualTo(CollabCursorColors.colorOf(USER_ID));
    }

    @Test
    void createSession_expiresAtShouldMatchTokenExpAndBeIsoUtc() {
        givenList();
        givenUpdatePermission(true);
        givenDisplayName("张三");

        CollabSessionResponse response = service()
                .createSession(LIST_ID, new SecurityPrincipal(USER_ID, "zhangsan", "", true, 0L));
        CollabTokenClaims claims = tokenService().verify(response.token(), response.room());

        // ISO-8601 UTC 秒级（如 2026-11-14T09:30:00Z），与 token 的 exp 是同一时刻。
        assertThat(response.expiresAt()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$");
        assertThat(Instant.parse(response.expiresAt())).isEqualTo(claims.expiresAt());
        assertThat(claims.expiresAt())
                .isAfter(Instant.now().plus(9, ChronoUnit.MINUTES));
    }

    @Test
    void createSession_withoutPrincipal_shouldThrowUnauthorized() {
        assertThatThrownBy(() -> service().createSession(LIST_ID, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private SupplierPriceCollabSessionService service() {
        return new SupplierPriceCollabSessionService(repository, permissionChecker, accountQuery,
                tokenService(), properties);
    }

    private CollabTokenService tokenService() {
        return new CollabTokenService(properties, objectMapper);
    }

    private CollabSessionProperties properties() {
        CollabSessionProperties collabProperties = new CollabSessionProperties();
        collabProperties.setJwtSecret(CollabSessionProperties.DEV_INSECURE_JWT_SECRET);
        return collabProperties;
    }

    private void givenList() {
        SupplierPriceList list = new SupplierPriceList();
        list.setId(LIST_ID);
        list.setSupplierId(SUPPLIER_ID);
        list.setSupplierName("某某钢厂");
        lenient().when(repository.findByIdAndDeletedFlagFalse(LIST_ID)).thenReturn(Optional.of(list));
    }

    private void givenUpdatePermission(boolean granted) {
        lenient().when(permissionChecker.has(PermissionCodes.SUPPLIER_PRICE_LISTS_UPDATE))
                .thenReturn(granted);
    }

    private void givenDisplayName(String userName) {
        lenient().when(accountQuery.findById(anyLong())).thenReturn(Optional.of(
                new AccountQuery.AccountSnapshot(USER_ID, "zhangsan", userName, null)));
    }
}
