package com.leo.erp.common.idempotent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.security.support.SecurityPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
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
        verify(service, never()).release(any());
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
        verify(service).release(any());
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

    private MockHttpServletRequest postRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/purchase-orders");
        request.addHeader(HttpIdempotencyFilter.HEADER, "key-1");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
