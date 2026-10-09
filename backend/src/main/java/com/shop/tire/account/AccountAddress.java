package com.shop.tire.account;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "account_address")
class AccountAddress {
    @Id UUID accountId;
    @Column(length = 200) String recipientName;
    @Column(length = 200) String street;
    @Column(length = 20) String buildingNumber;
    @Column(length = 20) String apartmentNumber;
    @Column(length = 20) String postalCode;
    @Column(length = 100) String city;
    @Column(length = 2) String countryCode;
    @Version long version;

    protected AccountAddress() {}
    AccountAddress(UUID accountId) { this.accountId = accountId; }

    void update(AccountRequests.AddressUpdate value) {
        recipientName = value.recipientName().strip();
        street = value.street().strip();
        buildingNumber = value.buildingNumber().strip();
        apartmentNumber = value.apartmentNumber() == null ? null : value.apartmentNumber().strip();
        postalCode = value.postalCode().strip();
        city = value.city().strip();
        countryCode = value.countryCode();
    }

    AccountRequests.AddressUpdate response() {
        return new AccountRequests.AddressUpdate(recipientName, street, buildingNumber,
                apartmentNumber, postalCode, city, countryCode);
    }
}
