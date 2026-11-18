package com.shop.tire.account.repository;

import com.shop.tire.account.entity.AccountAddress;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<AccountAddress, UUID> {}
