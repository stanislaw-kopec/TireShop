package com.shop.tire.order.config;

import java.net.URI;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.tracking")
public record TrackingProperties(String secret, URI linkUrl) {
    public SecretKey encryptionKey() {
        byte[] key;
        try { key=Base64.getDecoder().decode(secret==null?"":secret); }
        catch (IllegalArgumentException ex) { throw invalidSecret(); }
        if (key.length!=32) throw invalidSecret();
        boolean local=linkUrl!=null && ("localhost".equals(linkUrl.getHost()) || "127.0.0.1".equals(linkUrl.getHost()));
        if (linkUrl==null || linkUrl.getHost()==null || linkUrl.getUserInfo()!=null
                || linkUrl.getQuery()!=null || linkUrl.getFragment()!=null
                || !("https".equals(linkUrl.getScheme()) || (local && "http".equals(linkUrl.getScheme())))) {
            throw new IllegalStateException("TRACKING_LINK_URL must be a trusted HTTPS URL (local HTTP allowed), without query, fragment or credentials.");
        }
        return new SecretKeySpec(key,"AES");
    }
    private static IllegalStateException invalidSecret() {
        return new IllegalStateException("TRACKING_SECRET must be Base64 containing exactly 32 random bytes.");
    }
    @Override public String toString() { return "TrackingProperties[redacted]"; }
}
