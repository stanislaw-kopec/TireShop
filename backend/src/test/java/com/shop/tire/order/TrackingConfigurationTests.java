package com.shop.tire.order;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TrackingConfigurationTests {
    private String secret() { return Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32)); }

    @Test
    void missingMalformedAndWrongSizedKeyStopStartupWithoutDisclosingValue() {
        for (String invalid:new String[]{"", "not-a-base64-secret", Base64.getEncoder().encodeToString(new byte[31])}) {
            assertThatThrownBy(()->new TrackingSecrets(new TrackingProperties(invalid,URI.create("http://localhost:4200/track-order"))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("TRACKING_SECRET must be Base64 containing exactly 32 random bytes.");
        }
    }

    @Test
    void configuredLinksRejectCredentialsQueriesFragmentsAndRemoteHttp() {
        for (String invalid:new String[]{"http://shop.example/track","https://user:password@shop.example/track",
                "https://shop.example/track?token=x","https://shop.example/track#existing","/relative"}) {
            assertThatThrownBy(()->new TrackingSecrets(new TrackingProperties(secret(),URI.create(invalid))))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThatCode(()->new TrackingSecrets(new TrackingProperties(secret(),URI.create("https://shop.example/track")))).doesNotThrowAnyException();
    }

    @Test
    void encryptedTokensSurviveRestartAndRejectModificationOrDifferentKey() {
        var properties=new TrackingProperties(secret(),URI.create("http://localhost:4200/track-order"));
        var first=new TrackingSecrets(properties);
        String token=com.shop.tire.shared.security.OpaqueTokens.create();
        byte[] encrypted=first.encrypt(token);
        assertThat(first.encrypt(token)).isNotEqualTo(encrypted);
        assertThat(new TrackingSecrets(properties).decrypt(encrypted)).isEqualTo(token);
        assertThatThrownBy(()->new TrackingSecrets(new TrackingProperties(secret(),properties.linkUrl())).decrypt(encrypted))
                .isInstanceOf(RuntimeException.class);
        encrypted[encrypted.length-1]^=1;
        assertThatThrownBy(()->first.decrypt(encrypted)).isInstanceOf(RuntimeException.class);
        assertThat(properties.toString()).doesNotContain(properties.secret());
    }
}
