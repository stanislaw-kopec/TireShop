package com.shop.tire.order.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public final class FulfillmentRequests {
    private FulfillmentRequests() {}
    public enum Target { PROCESSING, SHIPPED, DELIVERED }
    static final String TRACKING_FORMAT="[A-Za-z0-9][A-Za-z0-9._/-]{0,99}";

    public record StatusChange(@NotNull UUID operationId, @NotNull Target status,
                               @Pattern(regexp=TRACKING_FORMAT) String trackingNumber) {
        public StatusChange { trackingNumber=trackingNumber==null?null:trackingNumber.strip(); }
    }
    public record Shipment(@NotNull UUID operationId,
                           @NotBlank @Pattern(regexp=TRACKING_FORMAT) String trackingNumber,
                           @NotBlank @Pattern(regexp=TRACKING_FORMAT) String expectedTrackingNumber) {
        public Shipment {
            trackingNumber=trackingNumber==null?null:trackingNumber.strip();
            expectedTrackingNumber=expectedTrackingNumber==null?null:expectedTrackingNumber.strip();
        }
    }
}
