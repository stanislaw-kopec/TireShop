package com.shop.tire.order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record OrderSummary(UUID id, String orderNumber, OrderStatus status, Instant createdAt, BigDecimal totalGross, String currency) {}
