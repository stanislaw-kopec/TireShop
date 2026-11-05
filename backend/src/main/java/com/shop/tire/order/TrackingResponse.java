package com.shop.tire.order;

public record TrackingResponse(String orderNumber, OrderStatus status, String trackingNumber) {}
