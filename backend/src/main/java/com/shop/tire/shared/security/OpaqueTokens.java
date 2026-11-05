package com.shop.tire.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class OpaqueTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private OpaqueTokens() {}
    public static String create() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static boolean valid(String token) { return token != null && token.matches("[A-Za-z0-9_-]{43}"); }
    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
}
