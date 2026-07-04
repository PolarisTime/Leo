package com.leo.erp.common.idempotent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.security.support.SecurityPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpIdempotencyFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsWriteRequestWithoutIdempotencyHeader() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/purchase-orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(service, never()).start(any(), any(), any());
    }

    @Test
    void allowsWriteRequestWithBlankIdempotencyHeader() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/purchase-orders");
        request.addHeader(HttpIdempotencyFilter.HEADER, " ");
        request.addHeader(HttpIdempotencyFilter.LEGACY_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(service, never()).start(any(), any(), any());
    }

    @Test
    void allowsGetRequestWithIdempotencyHeader() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/purchase-orders");
        request.addHeader(HttpIdempotencyFilter.HEADER, "key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(service, never()).start(any(), any(), any());
    }

    @Test
    void firstWriteRequestPassesReplayableBodyAndCompletes() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.ACQUIRED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletRequest request = postRequest("{\"orderNo\":\"PO-001\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilterInternal(request, response, (req, res) -> {
            chainInvoked.set(true);
            String body = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).isEqualTo("{\"orderNo\":\"PO-001\"}");
            ((MockHttpServletResponse) res).setStatus(200);
        });

        assertThat(chainInvoked.get()).isTrue();
        verify(service).markCompleted(any(), any(), eq(Duration.ofHours(24)));
        verify(service, never()).release(any(), any());
    }

    @Test
    void usesTrimmedLegacyIdempotencyHeaderWhenPrimaryHeaderIsBlank() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.DUPLICATE_COMPLETED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/purchase-orders");
        request.addHeader(HttpIdempotencyFilter.HEADER, " ");
        request.addHeader(HttpIdempotencyFilter.LEGACY_HEADER, "  legacy-key  ");
        request.setContentType("application/json");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, mock(FilterChain.class));

        verify(service).start(
                eq(scopedKey("anonymous", "POST", "/purchase-orders", "legacy-key")),
                any(),
                eq(Duration.ofHours(24))
        );
    }

    @Test
    void fingerprintsUsePathWithoutContextPath() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.DUPLICATE_COMPLETED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        byte[] body = "{\"orderNo\":\"PO-001\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/erp/purchase-orders");
        request.setContextPath("/erp");
        request.setQueryString("source=mobile");
        request.addHeader(HttpIdempotencyFilter.HEADER, "key-1");
        request.setContentType("application/json");
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, mock(FilterChain.class));

        verify(service).start(
                eq(scopedKey("anonymous", "POST", "/purchase-orders", "key-1")),
                eq(fingerprint("POST", "/purchase-orders", "source=mobile", body)),
                eq(Duration.ofHours(24))
        );
    }

    @Test
    void fingerprintsUseOriginalPathWhenContextPathDoesNotMatch() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.DUPLICATE_COMPLETED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/purchase-orders");
        request.setContextPath("/erp");
        request.addHeader(HttpIdempotencyFilter.HEADER, "key-1");
        request.setContentType("application/json");
        request.setContent(body);

        filter.doFilterInternal(request, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(service).start(
                eq(scopedKey("anonymous", "POST", "/purchase-orders", "key-1")),
                eq(fingerprint("POST", "/purchase-orders", "", body)),
                eq(Duration.ofHours(24))
        );
    }

    @Test
    void fingerprintsUseOriginalPathWhenContextPathIsNull() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.DUPLICATE_COMPLETED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/purchase-orders") {
            @Override
            public String getContextPath() {
                return null;
            }
        };
        request.addHeader(HttpIdempotencyFilter.HEADER, "key-1");
        request.setContentType("application/json");
        request.setContent(body);

        filter.doFilterInternal(request, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(service).start(
                eq(scopedKey("anonymous", "POST", "/purchase-orders", "key-1")),
                eq(fingerprint("POST", "/purchase-orders", "", body)),
                eq(Duration.ofHours(24))
        );
    }

    @Test
    void enforcesWriteRequestWhenContentTypeIsNull() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.DUPLICATE_COMPLETED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/purchase-orders");
        request.addHeader(HttpIdempotencyFilter.HEADER, "key-1");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));

        filter.doFilterInternal(request, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(service).start(any(), any(), eq(Duration.ofHours(24)));
    }

    @Test
    void releasesKeyWhenResponseIsNotSuccessful() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.ACQUIRED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(postRequest("{}"), response, (req, res) ->
                ((MockHttpServletResponse) res).setStatus(500)
        );

        verify(service).release(any(), any());
        verify(service, never()).markCompleted(any(), any(), any());
    }

    @Test
    void releasesKeyWhenResponseIsInformational() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.ACQUIRED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(postRequest("{}"), response, (req, res) ->
                ((MockHttpServletResponse) res).setStatus(100)
        );

        verify(service).release(any(), any());
        verify(service, never()).markCompleted(any(), any(), any());
    }

    @Test
    void duplicatePendingDoesNotInvokeChain() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.DUPLICATE_PENDING));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(postRequest("{}"), response, chain);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(body.path("code").asInt()).isEqualTo(4220);
        assertThat(body.path("message").asText()).contains("请勿重复提交");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void duplicateCompletedReturnsHandledSuccess() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.DUPLICATE_COMPLETED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(postRequest("{}"), response, chain);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("message").asText()).contains("请求已处理");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void nullDecisionStatusPropagatesFailure() {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(null));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);

        assertThatThrownBy(() -> filter.doFilterInternal(postRequest("{}"), new MockHttpServletResponse(), mock(FilterChain.class)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void mismatchReturnsBusinessError() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.PARAMETER_MISMATCH));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(postRequest("{}"), response, chain);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(body.path("message").asText()).contains("幂等键已用于不同请求");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void releasesKeyWhenChainThrowsRuntimeException() {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.ACQUIRED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilterInternal(postRequest("{}"), response, (req, res) -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        verify(service).release(any(), any());
    }

    @Test
    void releasesKeyWhenChainThrowsIOException() {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.ACQUIRED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();
        IOException failure = new IOException("boom");

        assertThatThrownBy(() -> filter.doFilterInternal(postRequest("{}"), response, (req, res) -> {
            throw failure;
        })).isSameAs(failure);
        verify(service).release(any(), any());
    }

    @Test
    void releasesKeyWhenChainThrowsServletException() {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.ACQUIRED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletException failure = new ServletException("boom");

        assertThatThrownBy(() -> filter.doFilterInternal(postRequest("{}"), response, (req, res) -> {
            throw failure;
        })).isSameAs(failure);
        verify(service).release(any(), any());
    }

    @Test
    void skipsMultipartWriteRequestEvenWithIdempotencyHeader() throws Exception {
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload");
        request.addHeader(HttpIdempotencyFilter.HEADER, "key-1");
        request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(service, never()).start(any(), any(), any());
    }

    @Test
    void scopedKeyIncludesCurrentUser() throws Exception {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(7L, "tester", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.ACQUIRED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(postRequest("{}"), response, (req, res) ->
                ((MockHttpServletResponse) res).setStatus(200)
        );

        verify(service).start(any(), any(), eq(Duration.ofHours(24)));
        verify(service).markCompleted(any(), any(), eq(Duration.ofHours(24)));
    }

    @Test
    void scopedKeyUsesExpectedPrincipalScope() throws Exception {
        assertScopedKeyForAuthentication(null, "anonymous");
        assertScopedKeyForAuthentication(
                new UsernamePasswordAuthenticationToken("guest", null),
                "anonymous"
        );
        assertScopedKeyForAuthentication(
                new UsernamePasswordAuthenticationToken(
                        SecurityPrincipal.authenticated(7L, "tester", List.of()),
                        null,
                        List.of()
                ),
                "user:7"
        );
        assertScopedKeyForAuthentication(
                new UsernamePasswordAuthenticationToken("operator", null, List.of()),
                "auth:operator"
        );

        Authentication blankNameAuthentication = mock(Authentication.class);
        when(blankNameAuthentication.isAuthenticated()).thenReturn(true);
        when(blankNameAuthentication.getPrincipal()).thenReturn(new Object());
        when(blankNameAuthentication.getName()).thenReturn(" ");
        assertScopedKeyForAuthentication(blankNameAuthentication, "authenticated");

        Authentication nullNameAuthentication = mock(Authentication.class);
        when(nullNameAuthentication.isAuthenticated()).thenReturn(true);
        when(nullNameAuthentication.getPrincipal()).thenReturn(new Object());
        when(nullNameAuthentication.getName()).thenReturn(null);
        assertScopedKeyForAuthentication(nullNameAuthentication, "authenticated");
    }

    @Test
    void sha256HexShouldWrapMissingAlgorithm() {
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(mock(HttpIdempotencyService.class), objectMapper);

        try (var messageDigest = mockStatic(MessageDigest.class)) {
            messageDigest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("missing"));

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(filter, "sha256Hex", new byte[]{1}))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHA-256 is not available");
        }
    }

    private void assertScopedKeyForAuthentication(Authentication authentication, String principalScope) throws Exception {
        SecurityContextHolder.clearContext();
        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        HttpIdempotencyService service = mock(HttpIdempotencyService.class);
        when(service.start(any(), any(), eq(Duration.ofHours(24))))
                .thenReturn(new HttpIdempotencyService.Decision(HttpIdempotencyService.Status.DUPLICATE_COMPLETED));
        HttpIdempotencyFilter filter = new HttpIdempotencyFilter(service, objectMapper);

        filter.doFilterInternal(postRequest("{}"), new MockHttpServletResponse(), mock(FilterChain.class));

        verify(service).start(
                eq(scopedKey(principalScope, "POST", "/purchase-orders", "key-1")),
                any(),
                eq(Duration.ofHours(24))
        );
    }

    private MockHttpServletRequest postRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/purchase-orders");
        request.addHeader(HttpIdempotencyFilter.HEADER, "key-1");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private String scopedKey(String principalScope, String method, String path, String idempotencyKey) {
        return sha256Hex((principalScope + "\n" + method + "\n" + path + "\n" + idempotencyKey)
                .getBytes(StandardCharsets.UTF_8));
    }

    private String fingerprint(String method, String path, String queryString, byte[] body) {
        String raw = method + "\n" + path + "\n" + queryString + "\n" + sha256Hex(body);
        return sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
