package com.shop.tire.order.dto;

import java.time.Instant;

public record TrackingLinkResponse(String trackingUrl, Instant expiresAt) {
    @Override public String toString() { return "TrackingLinkResponse[redacted]"; }
}
