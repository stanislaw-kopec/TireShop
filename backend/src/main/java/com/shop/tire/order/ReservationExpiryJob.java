package com.shop.tire.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name="app.order.expiry-job-enabled",havingValue="true",matchIfMissing=true)
class ReservationExpiryJob {
    private final OrderService orders;
    ReservationExpiryJob(OrderService orders) { this.orders=orders; }
    @Scheduled(fixedDelayString="${app.order.expiry-delay-ms:15000}")
    void expire() {
        for (var id : orders.expiredIds()) {
            try { orders.expire(id); }
            catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).error("Reservation release failed for order {} ({})",id,ex.getClass().getSimpleName());
            }
        }
    }
}
