package com.shop.tire.order.dto;

import com.shop.tire.order.model.OrderStatus;

public record TrackingResponse(String orderNumber, OrderStatus status, String trackingNumber) {}
