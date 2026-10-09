package com.shop.tire.account;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AddressRepository extends JpaRepository<AccountAddress, UUID> {}
