package com.shop.tire.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@org.springframework.core.annotation.Order(0)
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
class AdminBootstrap implements ApplicationRunner {
    private final AdminBootstrapService service;
    private final String email;
    private final String password;

    AdminBootstrap(AdminBootstrapService service, @Value("${app.bootstrap.admin-email:}") String email,
                   @Value("${app.bootstrap.admin-password:}") String password) {
        this.service = service;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) { service.create(email, password); }
}
