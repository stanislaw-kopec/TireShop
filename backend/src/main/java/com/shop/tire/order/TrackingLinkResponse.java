package com.shop.tire.order;

import java.time.Instant;

public record TrackingLinkResponse(String trackingUrl, Instant expiresAt) {
    @Override public String toString() { return "TrackingLinkResponse[redacted]"; }
}
