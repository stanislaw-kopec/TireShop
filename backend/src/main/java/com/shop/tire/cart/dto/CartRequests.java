package com.shop.tire.cart.dto;
import jakarta.validation.constraints.*;
public final class CartRequests {
    private CartRequests() {}
    public record Quantity(@NotNull @Min(1) Integer quantity) {}
}
