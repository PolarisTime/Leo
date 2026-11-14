package com.leo.erp.market.quotation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.api.AccountQuery;
import com.leo.erp.common.collab.CollabCursorColors;
import com.leo.erp.common.collab.CollabSessionIssuer;
import com.leo.erp.common.collab.CollabSessionProperties;
import com.leo.erp.common.collab.CollabSessionResponse;
import com.leo.erp.common.collab.CollabTokenClaims;
import com.leo.erp.common.collab.CollabTokenService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.security.permission.PermissionChecker;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.support.SecurityPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 报单比价协作会话签发口径。
 *
 * <p>与供应商价格表版本**同一套票据机制**，这里锁的是「只有房间与权限口径不同」：</p>
 * <ul>
 *   <li>房间固定 {@code quote-sheet:<sheetId>}（一张报价单一个房间）；</li>
 *   <li>writable 由 {@code quote-sheets:update} 决定，只读用户也能取票（看板场景）；</li>
 *   <li>claims 集合/顺序与供应商侧逐字节同构 —— 用同一密钥、同一时刻、同一 writable 各签一张，
 *       把 room 值替换后两份 payload 的 JSON 文本必须完全相等；</li>
 *   <li>401 先于 404：未登录时不能通过状态码差异探出单据是否存在。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class QuoteSheetCollabSessionServiceTest {

    private static final long SHEET_ID = 900000000000000301L;
    private static final long USER_ID = 900000000000000302L;
    private static final String QUOTE_ROOM = "quote-sheet:" + SHEET_ID;
    private static final String SUPPLIER_ROOM = "supplier-price-list:" + SHEET_ID;

    @Mock
    private QuoteSheetRepository repository;

    @Mock
    private PermissionChecker permissionChecker;

    @Mock
    private AccountQuery accountQuery;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CollabSessionProperties properties = properties();

    @Test
    void createSession_shouldUseQuoteSheetRoomPrefix() {
        givenSheetExists();
        givenUpdatePermission(true);
        givenDisplayName("张三");

        CollabSessionResponse response = service()
                .createSession(SHEET_ID, new SecurityPrincipal(USER_ID, "zhangsan", "", true, 0L));

        assertThat(response.room()).isEqualTo(QUOTE_ROOM);
        assertThat(response.url()).isEqualTo("ws://127.0.0.1:11212/collab");
    }

    @Test
    void createSession_withUpdatePermission_shouldBeWritableAndTokenCarriesIt() {
        givenSheetExists();
        givenUpdatePermission(true);
        givenDisplayName("张三");
        SecurityPrincipal principal = new SecurityPrincipal(USER_ID, "zhangsan", "", true, 0L);

        CollabSessionResponse response = service().createSession(SHEET_ID, principal);
        CollabTokenClaims claims = tokenService().verify(response.token(), response.room());

        assertThat(response.writable()).isTrue();
        assertThat(claims.userId()).isEqualTo(String.valueOf(USER_ID));
        assertThat(claims.room()).isEqualTo(QUOTE_ROOM);
        assertThat(claims.writable()).isTrue();
        assertThat(claims.name()).isEqualTo("张三");
        assertThat(claims.color()).isEqualTo(response.color());
    }

    @Test
    void createSession_withoutUpdatePermission_shouldBeReadOnlyButStillIssueToken() {
        givenSheetExists();
        givenUpdatePermission(false);
        givenDisplayName("李四");
        SecurityPrincipal principal = new SecurityPrincipal(USER_ID, "lisi", "", true, 0L);

        CollabSessionResponse response = service().createSession(SHEET_ID, principal);
        CollabTokenClaims claims = tokenService().verify(response.token(), response.room());

        assertThat(response.writable()).isFalse();
        assertThat(claims.writable()).isFalse();
        assertThat(response.token()).isNotBlank();
    }

    @Test
    void createSession_shouldQueryWritePermissionForQuoteSheets() {
        givenSheetExists();
        givenUpdatePermission(true);
        givenDisplayName("张三");

        service().createSession(SHEET_ID, new SecurityPrincipal(USER_ID, "zhangsan", "", true, 0L));

        verify(permissionChecker).has(PermissionCodes.QUOTE_SHEETS_UPDATE);
    }

    @Test
    void createSession_whenSheetMissing_shouldThrowNotFound() {
        when(repository.findVersionById(SHEET_ID)).thenReturn(null);

        assertThatThrownBy(() -> service()
                .createSession(SHEET_ID, new SecurityPrincipal(USER_ID, "zhangsan", "", true, 0L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void createSession_withoutPrincipal_shouldThrowUnauthorizedBeforeProbingSheet() {
        assertThatThrownBy(() -> service().createSession(SHEET_ID, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        // 401 先于 404：未登录时一次库都不查。
        verify(repository, never()).findVersionById(anyLong());
    }

    @Test
    void createSession_colorShouldBeHexAndStableForSameUser() {
        givenSheetExists();
        givenUpdatePermission(true);
        givenDisplayName("张三");
        SecurityPrincipal principal = new SecurityPrincipal(USER_ID, "zhangsan", "", true, 0L);

        String first = service().createSession(SHEET_ID, principal).color();
        String second = service().createSession(SHEET_ID, principal).color();

        assertThat(first).matches("^#[0-9a-f]{6}$");
        assertThat(second).isEqualTo(first);
        assertThat(first).isEqualTo(CollabCursorColors.colorOf(USER_ID));
    }

    @Test
    void createSession_expiresAtShouldMatchTokenExpAndBeIsoUtc() {
        givenSheetExists();
        givenUpdatePermission(true);
        givenDisplayName("张三");

        CollabSessionResponse response = service()
                .createSession(SHEET_ID, new SecurityPrincipal(USER_ID, "zhangsan", "", true, 0L));
        CollabTokenClaims claims = tokenService().verify(response.token(), response.room());

        assertThat(response.expiresAt()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$");
        assertThat(Instant.parse(response.expiresAt())).isEqualTo(claims.expiresAt());
        assertThat(claims.expiresAt()).isAfter(Instant.now().plus(9, ChronoUnit.MINUTES));
    }

    @Test
    void issuedToken_shouldBeClaimIsomorphicToSupplierPriceSide() throws Exception {
        Instant fixedExpiry = Instant.ofEpochSecond(1_763_101_800L);
        String color = CollabCursorColors.colorOf(USER_ID);

        // 同一密钥 / 同一用户 / 同一过期时刻 / 同一 writable，唯一差别是 room。
        String supplierToken = tokenService().issue(USER_ID, "张三", SUPPLIER_ROOM, true, color, fixedExpiry);
        String quoteToken = tokenService().issue(USER_ID, "张三", QUOTE_ROOM, true, color, fixedExpiry);

        String supplierPayload = payloadJson(supplierToken).toString();
        String quotePayload = payloadJson(quoteToken).toString();

        // 把 room 值换成同一个再比：两份 payload 的键集合、键顺序、其余取值必须完全一致。
        assertThat(quotePayload).isEqualTo(supplierPayload.replace(SUPPLIER_ROOM, QUOTE_ROOM));
        // 头也必须完全一致（alg/typ）。
        assertThat(segment(quoteToken, 0)).isEqualTo(segment(supplierToken, 0));
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private QuoteSheetCollabSessionService service() {
        return new QuoteSheetCollabSessionService(repository, permissionChecker, accountQuery,
                sessionIssuer());
    }

    private CollabSessionIssuer sessionIssuer() {
        return new CollabSessionIssuer(tokenService(), properties);
    }

    private CollabTokenService tokenService() {
        return new CollabTokenService(properties, objectMapper);
    }

    private CollabSessionProperties properties() {
        CollabSessionProperties collabProperties = new CollabSessionProperties();
        collabProperties.setJwtSecret(CollabSessionProperties.DEV_INSECURE_JWT_SECRET);
        return collabProperties;
    }

    private void givenSheetExists() {
        lenient().when(repository.findVersionById(SHEET_ID)).thenReturn(7L);
    }

    private void givenUpdatePermission(boolean granted) {
        lenient().when(permissionChecker.has(PermissionCodes.QUOTE_SHEETS_UPDATE)).thenReturn(granted);
    }

    private void givenDisplayName(String userName) {
        lenient().when(accountQuery.findById(anyLong())).thenReturn(Optional.of(
                new AccountQuery.AccountSnapshot(USER_ID, "zhangsan", userName, null)));
    }

    private JsonNode payloadJson(String token) throws Exception {
        return objectMapper.readTree(
                Base64.getUrlDecoder().decode(segment(token, 1)));
    }

    private String segment(String token, int index) {
        String[] parts = token.split("\\.");
        return parts[index];
    }
}
