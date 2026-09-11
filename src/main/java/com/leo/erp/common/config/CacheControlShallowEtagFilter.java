package com.leo.erp.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * 在 Spring {@link ShallowEtagHeaderFilter} 基础上为安全的读取请求补充保守的
 * {@code Cache-Control: no-cache}，并跳过附件、导出等二进制下载的响应缓存，
 * 避免大文件被整体缓冲进内存。
 */
public class CacheControlShallowEtagFilter extends ShallowEtagHeaderFilter {

    static final String CACHE_CONTROL_NO_CACHE = "no-cache";
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD");

    public CacheControlShallowEtagFilter() {
        setWriteWeakETag(true);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isBinaryDownload(request)) {
            ShallowEtagHeaderFilter.disableContentCaching(request);
        }
        super.doFilterInternal(request, response, filterChain);
    }

    @Override
    protected boolean isEligibleForEtag(HttpServletRequest request,
                                        HttpServletResponse response,
                                        int responseStatusCode,
                                        InputStream inputStream) {
        boolean readRequest = READ_METHODS.contains(request.getMethod());
        if (readRequest && response.getHeader(HttpHeaders.CACHE_CONTROL) == null) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_CACHE);
        }
        if ("HEAD".equals(request.getMethod())) {
            return isEligibleHead(response, responseStatusCode);
        }
        return super.isEligibleForEtag(request, response, responseStatusCode, inputStream);
    }

    private boolean isEligibleHead(HttpServletResponse response, int responseStatusCode) {
        if (response.isCommitted() || responseStatusCode < 200 || responseStatusCode >= 300) {
            return false;
        }
        String cacheControl = response.getHeader(HttpHeaders.CACHE_CONTROL);
        return cacheControl == null || !cacheControl.contains("no-store");
    }

    private boolean isBinaryDownload(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return uri.endsWith("/content")
                || uri.endsWith("/export")
                || uri.endsWith("/template")
                || uri.endsWith("/template/csv");
    }
}
