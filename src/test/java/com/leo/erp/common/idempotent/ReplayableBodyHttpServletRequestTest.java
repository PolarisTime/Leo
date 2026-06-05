package com.leo.erp.common.idempotent;

import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayableBodyHttpServletRequestTest {

    @Test
    void shouldExposeIndependentInputStreams() throws Exception {
        ReplayableBodyHttpServletRequest request = new ReplayableBodyHttpServletRequest(
                new MockHttpServletRequest(),
                "abc".getBytes(StandardCharsets.UTF_8)
        );

        ServletInputStream first = request.getInputStream();
        assertThat(first.isReady()).isTrue();
        assertThat(first.isFinished()).isFalse();
        assertThat((char) first.read()).isEqualTo('a');
        assertThat(first.readAllBytes()).isEqualTo("bc".getBytes(StandardCharsets.UTF_8));
        assertThat(first.isFinished()).isTrue();

        assertThat(request.getInputStream().readAllBytes()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldReadBodyWithUtf8WhenEncodingIsBlank() throws Exception {
        MockHttpServletRequest delegate = new MockHttpServletRequest();
        delegate.setCharacterEncoding(" ");
        ReplayableBodyHttpServletRequest request = new ReplayableBodyHttpServletRequest(
                delegate,
                "中文".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(request.getReader().readLine()).isEqualTo("中文");
    }

    @Test
    void shouldReadBodyWithRequestEncoding() throws Exception {
        MockHttpServletRequest delegate = new MockHttpServletRequest();
        delegate.setCharacterEncoding("GBK");
        ReplayableBodyHttpServletRequest request = new ReplayableBodyHttpServletRequest(
                delegate,
                "中文".getBytes(Charset.forName("GBK"))
        );

        assertThat(request.getReader().readLine()).isEqualTo("中文");
    }

    @Test
    void shouldTreatNullBodyAsEmpty() throws Exception {
        ReplayableBodyHttpServletRequest request = new ReplayableBodyHttpServletRequest(
                new MockHttpServletRequest(),
                null
        );

        assertThat(request.getInputStream().isFinished()).isTrue();
        assertThat(request.getReader().readLine()).isNull();
    }
}
