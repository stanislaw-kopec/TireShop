package com.shop.tire.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name="app.tracking.mail-job-enabled",havingValue="true",matchIfMissing=true)
class OrderMailJob {
    private final OrderMailDelivery delivery;
    OrderMailJob(OrderMailDelivery delivery) { this.delivery=delivery; }
    @Scheduled(fixedDelay=15000)
    void deliver() {
        delivery.cleanExpiredSecrets();
        for (int i=0;i<20 && delivery.sendNext();i++) { /* Separate transaction for each message. */ }
    }
}
