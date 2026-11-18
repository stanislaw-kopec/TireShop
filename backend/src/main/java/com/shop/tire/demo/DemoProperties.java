package com.shop.tire.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.demo")
public record DemoProperties(String password) {
    public void requirePassword() {
        if (password == null || password.isBlank() || password.length() < 12 || password.length() > 128) {
            throw new IllegalStateException("Demo seed or upgrade requires DEMO_PASSWORD (12-128 characters).");
        }
    }
    @Override public String toString() { return "DemoProperties[redacted]"; }
}
