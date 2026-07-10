package com.leo.erp.system.setup.service;

import com.leo.erp.system.setup.config.InitialSetupProperties;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;

@Component
public class SetupTokenVerifier {

    private static final int TOKEN_BYTES = 32;
    private static final Pattern BASE64_URL_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}=?");

    private final DecodedToken configuredToken;

    public SetupTokenVerifier(InitialSetupProperties properties) {
        this.configuredToken = decode(properties.getBootstrapToken());
    }

    public boolean matches(String providedToken) {
        DecodedToken candidate = decode(providedToken);
        boolean bytesMatch = MessageDigest.isEqual(configuredToken.bytes(), candidate.bytes());
        return configuredToken.valid() & candidate.valid() & bytesMatch;
    }

    private static DecodedToken decode(String token) {
        byte[] fixedLengthBytes = new byte[TOKEN_BYTES];
        if (token == null || !BASE64_URL_TOKEN.matcher(token).matches()) {
            return new DecodedToken(fixedLengthBytes, false);
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            if (decoded.length != TOKEN_BYTES) {
                return new DecodedToken(fixedLengthBytes, false);
            }
            System.arraycopy(decoded, 0, fixedLengthBytes, 0, TOKEN_BYTES);
            return new DecodedToken(fixedLengthBytes, true);
        } catch (IllegalArgumentException ignored) {
            return new DecodedToken(fixedLengthBytes, false);
        }
    }

    private record DecodedToken(byte[] bytes, boolean valid) {
    }
}
