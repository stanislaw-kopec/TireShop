package com.shop.tire.demo;

import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@Order(Ordered.LOWEST_PRECEDENCE)
@EnableConfigurationProperties(DemoProperties.class)
class DemoSeedRunner implements ApplicationRunner {
    private final DemoSeedService seed;
    DemoSeedRunner(DemoSeedService seed) { this.seed = seed; }
    @Override public void run(ApplicationArguments args) {
        boolean created = seed.apply();
        LoggerFactory.getLogger(DemoSeedRunner.class).info(created
                ? "Demo data created or upgraded. Credentials and presentation steps: docs/demo.md"
                : "Demo seed already applied; existing data left unchanged.");
    }
}
