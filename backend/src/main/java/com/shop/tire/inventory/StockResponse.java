package com.shop.tire.inventory;

import java.util.UUID;

public record StockResponse(UUID productId, int onHand, int reserved, int available) {}
