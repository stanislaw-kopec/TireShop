package com.shop.tire.inventory.dto;

import java.util.UUID;

public record StockResponse(UUID productId, int onHand, int reserved, int available) {}
