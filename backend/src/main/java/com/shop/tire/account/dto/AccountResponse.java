package com.shop.tire.account.dto;

import com.shop.tire.account.model.Role;
import java.util.UUID;

public record AccountResponse(UUID id, String email, String firstName, String lastName,
                              String phone, Role role, String status) {}
