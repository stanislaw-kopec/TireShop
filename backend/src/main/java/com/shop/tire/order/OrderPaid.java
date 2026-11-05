package com.shop.tire.order;

import java.time.Instant;
import java.util.UUID;

public record OrderPaid(UUID orderId, Instant paidAt) {}
