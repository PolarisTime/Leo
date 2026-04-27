package com.leo.erp.common.support;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves the true client IP from a request, with protection against
 * X-Forwarded-For spoofing when the request did not come from a trusted proxy.
 *
 * Trusted proxies can be single IPs (127.0.0.1, 10.0.0.1) or CIDR ranges
 * (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16).
 */
@Component
public class IpResolutionService {

    private static final Logger log = LoggerFactory.getLogger(IpResolutionService.class);

    private final List<TrustedProxyMatcher> trustedMatchers;

    public IpResolutionService(@Value("${leo.trusted-proxies:}") String trustedProxyList) {
        this.trustedMatchers = Stream.of(trustedProxyList.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(TrustedProxyMatcher::new)
                .collect(Collectors.toList());
    }

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String remoteAddr = request.getRemoteAddr();

        if (remoteAddr == null || !isTrusted(remoteAddr)) {
            return remoteAddr != null ? remoteAddr : "unknown";
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remoteAddr;
        }

        // X-Forwarded-For: client, proxy1, proxy2
        String clientIp = forwarded.split(",")[0].trim();
        return clientIp.isEmpty() ? remoteAddr : clientIp;
    }

    public String resolveClientIpOrUnknown(HttpServletRequest request) {
        try {
            return resolveClientIp(request);
        } catch (Exception e) {
            log.debug("Failed to resolve client IP, returning 'unknown'", e);
            return "unknown";
        }
    }

    private boolean isTrusted(String ip) {
        for (TrustedProxyMatcher matcher : trustedMatchers) {
            if (matcher.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    private static final class TrustedProxyMatcher {
        private final String exactIp;
        private final int prefixLength;
        private final byte[] networkBytes;

        TrustedProxyMatcher(String spec) {
            int slash = spec.indexOf('/');
            if (slash < 0) {
                exactIp = spec;
                prefixLength = -1;
                networkBytes = null;
                return;
            }
            exactIp = null;
            prefixLength = Integer.parseInt(spec.substring(slash + 1));
            try {
                networkBytes = InetAddress.getByName(spec.substring(0, slash)).getAddress();
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid CIDR address: " + spec, e);
            }
        }

        boolean matches(String ip) {
            if (ip == null) return false;
            if (exactIp != null) return exactIp.equals(ip);
            try {
                byte[] addr = InetAddress.getByName(ip).getAddress();
                if (addr.length != networkBytes.length) return false;
                int fullBytes = prefixLength / 8;
                for (int i = 0; i < fullBytes; i++) {
                    if (addr[i] != networkBytes[i]) return false;
                }
                int remainingBits = prefixLength % 8;
                if (remainingBits > 0) {
                    int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                    if ((addr[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) return false;
                }
                return true;
            } catch (UnknownHostException e) {
                return false;
            }
        }
    }
}
