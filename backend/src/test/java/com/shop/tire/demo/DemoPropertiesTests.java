package com.shop.tire.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DemoPropertiesTests {
    @Test
    void passwordHasNoDefaultAndErrorsDoNotRevealIt() {
        for (String value : new String[]{null, "", "short", "x".repeat(129)}) {
            var properties = new DemoProperties(value);
            assertThatThrownBy(properties::requirePassword).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Demo seed or upgrade requires DEMO_PASSWORD (12-128 characters).");
            assertThat(properties.toString()).isEqualTo("DemoProperties[redacted]");
        }
        new DemoProperties("A valid demo password").requirePassword();
    }
}
