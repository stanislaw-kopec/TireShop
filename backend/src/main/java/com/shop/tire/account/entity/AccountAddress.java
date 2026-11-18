package com.shop.tire.account.entity;

import com.shop.tire.account.dto.AccountRequests;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "account_address")
public class AccountAddress {
    @Id private UUID accountId;
    @Column(length = 200) private String recipientName;
    @Column(length = 200) private String street;
    @Column(length = 20) private String buildingNumber;
    @Column(length = 20) private String apartmentNumber;
    @Column(length = 20) private String postalCode;
    @Column(length = 100) private String city;
    @Column(length = 2) private String countryCode;
    @Version private long version;

    protected AccountAddress() {}
    public AccountAddress(UUID accountId) { this.accountId = accountId; }

    public void update(AccountRequests.AddressUpdate value) {
        recipientName = value.recipientName().strip();
        street = value.street().strip();
        buildingNumber = value.buildingNumber().strip();
        apartmentNumber = value.apartmentNumber() == null ? null : value.apartmentNumber().strip();
        postalCode = value.postalCode().strip();
        city = value.city().strip();
        countryCode = value.countryCode();
    }

    public AccountRequests.AddressUpdate response() {
        return new AccountRequests.AddressUpdate(recipientName, street, buildingNumber,
                apartmentNumber, postalCode, city, countryCode);
    }
}
