package com.shop.tire.payment.dto;

import com.shop.tire.payment.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record PaymentResponse(UUID id, UUID orderId, BigDecimal amountGross, String currency, PaymentStatus status,
                              Instant createdAt, Instant finishedAt, String reason, boolean simulation) {}
