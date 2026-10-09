package com.shop.tire.account;

import jakarta.validation.constraints.*;

public final class AccountRequests {
    private AccountRequests() {}

    public record Registration(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName) {
        @Override public String toString() { return "Registration[redacted]"; }
    }

    public record ProfileUpdate(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 30) String phone) {}

    public record AddressUpdate(
            @NotBlank @Size(max = 200) String recipientName,
            @NotBlank @Size(max = 200) String street,
            @NotBlank @Size(max = 20) String buildingNumber,
            @Size(max = 20) String apartmentNumber,
            @NotBlank @Size(max = 20) String postalCode,
            @NotBlank @Size(max = 100) String city,
            @NotNull @Pattern(regexp = "[A-Z]{2}") String countryCode) {}

    public record EmployeeInvitation(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName) {}

    public record PasswordCompletion(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
            @NotBlank @Size(min = 12, max = 128) String password) {
        @Override public String toString() { return "PasswordCompletion[redacted]"; }
    }
}
