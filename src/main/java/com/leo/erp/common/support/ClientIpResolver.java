package com.leo.erp.common.support;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves the client IP without trusting caller-controlled forwarding headers.
 * Trusted proxies may be configured as IP literals or CIDR ranges.
 */
@Slf4j
@Component
public class ClientIpResolver {

    private static final int MAX_FORWARDED_HEADER_LENGTH = 1024;
    private static final int MAX_FORWARDED_HOPS = 8;

    private final List<TrustedProxyMatcher> trustedMatchers;

    public ClientIpResolver(@Value("${leo.trusted-proxies:}") String trustedProxyList) {
        this.trustedMatchers = Stream.of(trustedProxyList.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(TrustedProxyMatcher::new)
                .collect(Collectors.toList());
    }

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String remoteAddr = request.getRemoteAddr();
        String fallback = remoteAddr == null ? "unknown" : remoteAddr;
        IpLiteral current = IpLiteral.parse(remoteAddr);
        if (current == null || !isTrusted(current)) {
            return fallback;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return fallback;
        }
        if (forwarded.length() > MAX_FORWARDED_HEADER_LENGTH) {
            return fallback;
        }

        String[] hopValues = forwarded.split(",", -1);
        if (hopValues.length > MAX_FORWARDED_HOPS) {
            return fallback;
        }

        List<IpLiteral> hops = new ArrayList<>(hopValues.length);
        for (String hopValue : hopValues) {
            IpLiteral hop = IpLiteral.parse(hopValue.trim());
            if (hop == null) {
                return fallback;
            }
            hops.add(hop);
        }

        for (int index = hops.size() - 1; index >= 0 && isTrusted(current); index -= 1) {
            current = hops.get(index);
        }
        return current.value();
    }

    public String resolveClientIpOrUnknown(HttpServletRequest request) {
        try {
            return resolveClientIp(request);
        } catch (Exception exception) {
            log.debug("Failed to resolve client IP, returning 'unknown'", exception);
            return "unknown";
        }
    }

    private boolean isTrusted(IpLiteral ip) {
        for (TrustedProxyMatcher matcher : trustedMatchers) {
            if (matcher.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    private static final class TrustedProxyMatcher {

        private final byte[] networkBytes;
        private final int prefixLength;

        TrustedProxyMatcher(String spec) {
            int slash = spec.indexOf('/');
            if (slash >= 0 && spec.indexOf('/', slash + 1) >= 0) {
                throw invalidSpec(spec);
            }

            String addressValue = slash < 0 ? spec : spec.substring(0, slash);
            IpLiteral network = IpLiteral.parse(addressValue);
            if (network == null) {
                throw invalidSpec(spec);
            }

            int addressBits = network.bytes().length * Byte.SIZE;
            int parsedPrefix = addressBits;
            if (slash >= 0) {
                try {
                    parsedPrefix = Integer.parseInt(spec.substring(slash + 1));
                } catch (NumberFormatException exception) {
                    throw invalidSpec(spec);
                }
            }
            if (parsedPrefix < 0 || parsedPrefix > addressBits) {
                throw invalidSpec(spec);
            }

            this.networkBytes = network.bytes();
            this.prefixLength = parsedPrefix;
        }

        boolean matches(String ip) {
            IpLiteral literal = IpLiteral.parse(ip);
            return literal != null && matches(literal);
        }

        boolean matches(IpLiteral ip) {
            byte[] addressBytes = ip.bytes();
            if (addressBytes.length != networkBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / Byte.SIZE;
            for (int index = 0; index < fullBytes; index += 1) {
                if (addressBytes[index] != networkBytes[index]) {
                    return false;
                }
            }

            int remainingBits = prefixLength % Byte.SIZE;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (Byte.SIZE - remainingBits)) & 0xFF;
            return (addressBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
        }

        private static IllegalArgumentException invalidSpec(String spec) {
            return new IllegalArgumentException("Invalid trusted proxy IP/CIDR: " + spec);
        }
    }

    private record IpLiteral(String value, byte[] bytes) {

        static IpLiteral parse(String value) {
            if (value == null || value.isBlank() || !value.equals(value.trim())) {
                return null;
            }

            byte[] address;
            if (value.indexOf(':') >= 0) {
                address = parseIpv6(value);
            } else {
                address = parseIpv4(value);
            }
            return address == null ? null : new IpLiteral(value, address);
        }

        private static byte[] parseIpv4(String value) {
            String[] groups = value.split("\\.", -1);
            if (groups.length != 4) {
                return null;
            }

            byte[] result = new byte[4];
            for (int index = 0; index < groups.length; index += 1) {
                String group = groups[index];
                if (group.isEmpty() || group.length() > 3 || group.length() > 1 && group.charAt(0) == '0') {
                    return null;
                }
                int number = 0;
                for (int characterIndex = 0; characterIndex < group.length(); characterIndex += 1) {
                    char character = group.charAt(characterIndex);
                    if (character < '0' || character > '9') {
                        return null;
                    }
                    number = number * 10 + character - '0';
                }
                if (number > 255) {
                    return null;
                }
                result[index] = (byte) number;
            }
            return result;
        }

        private static byte[] parseIpv6(String value) {
            if (value.indexOf('%') >= 0 || value.startsWith("[") || value.endsWith("]")) {
                return null;
            }

            String normalized = replaceIpv4Suffix(value);
            if (normalized == null) {
                return null;
            }

            int compressionIndex = normalized.indexOf("::");
            if (compressionIndex >= 0 && normalized.indexOf("::", compressionIndex + 2) >= 0) {
                return null;
            }

            String leftValue = compressionIndex < 0 ? normalized : normalized.substring(0, compressionIndex);
            String rightValue = compressionIndex < 0 ? "" : normalized.substring(compressionIndex + 2);
            List<Integer> left = parseIpv6Groups(leftValue);
            List<Integer> right = parseIpv6Groups(rightValue);
            if (left == null || right == null) {
                return null;
            }

            int missingGroups = 8 - left.size() - right.size();
            if (compressionIndex < 0 && missingGroups != 0 || compressionIndex >= 0 && missingGroups < 1) {
                return null;
            }

            List<Integer> groups = new ArrayList<>(8);
            groups.addAll(left);
            for (int index = 0; index < missingGroups; index += 1) {
                groups.add(0);
            }
            groups.addAll(right);
            if (groups.size() != 8) {
                return null;
            }

            byte[] result = new byte[16];
            for (int index = 0; index < groups.size(); index += 1) {
                int group = groups.get(index);
                result[index * 2] = (byte) (group >>> 8);
                result[index * 2 + 1] = (byte) group;
            }
            return result;
        }

        private static String replaceIpv4Suffix(String value) {
            if (value.indexOf('.') < 0) {
                return value;
            }
            int lastColon = value.lastIndexOf(':');
            if (lastColon < 0) {
                return null;
            }
            byte[] ipv4 = parseIpv4(value.substring(lastColon + 1));
            if (ipv4 == null) {
                return null;
            }
            int high = (ipv4[0] & 0xFF) << 8 | ipv4[1] & 0xFF;
            int low = (ipv4[2] & 0xFF) << 8 | ipv4[3] & 0xFF;
            return value.substring(0, lastColon + 1) + Integer.toHexString(high) + ':' + Integer.toHexString(low);
        }

        private static List<Integer> parseIpv6Groups(String value) {
            if (value.isEmpty()) {
                return List.of();
            }

            String[] values = value.split(":", -1);
            List<Integer> result = new ArrayList<>(values.length);
            for (String group : values) {
                if (group.isEmpty() || group.length() > 4) {
                    return null;
                }
                int parsed = 0;
                for (int index = 0; index < group.length(); index += 1) {
                    int digit = Character.digit(group.charAt(index), 16);
                    if (digit < 0) {
                        return null;
                    }
                    parsed = parsed * 16 + digit;
                }
                result.add(parsed);
            }
            return result;
        }
    }
}
