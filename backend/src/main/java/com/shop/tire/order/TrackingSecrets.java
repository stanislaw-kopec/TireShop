package com.shop.tire.order;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.encrypt.*;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(TrackingProperties.class)
class TrackingSecrets {
    private final BytesEncryptor encryptor;
    TrackingSecrets(TrackingProperties properties) {
        encryptor=AesGcmBytesEncryptor.withSecretKey(properties.encryptionKey()).build();
    }
    byte[] encrypt(String value) { return encryptor.encrypt(value.getBytes(StandardCharsets.UTF_8)); }
    String decrypt(byte[] value) { return new String(encryptor.decrypt(value),StandardCharsets.UTF_8); }
}
