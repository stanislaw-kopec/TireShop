package com.shop.tire.account.service;

import com.shop.tire.account.dto.AccountRequests;
import com.shop.tire.account.dto.AccountResponse;
import com.shop.tire.account.entity.Account;
import com.shop.tire.account.entity.AccountAddress;
import com.shop.tire.account.model.Role;
import com.shop.tire.account.repository.AccountRepository;
import com.shop.tire.account.repository.AddressRepository;
import com.shop.tire.shared.error.ApiException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final AddressRepository addresses;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AccountService(AccountRepository accounts, AddressRepository addresses, PasswordEncoder encoder, Clock clock) {
        this.accounts = accounts;
        this.addresses = addresses;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    public AccountResponse register(AccountRequests.Registration input) {
        if (accounts.existsByEmail(Account.normalizeEmail(input.email()))) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_IN_USE", "Adres e-mail jest już zajęty.");
        }
        var account = new Account(input.email(), encoder.encode(input.password()), input.firstName(),
                input.lastName(), Role.CUSTOMER, clock.instant());
        return response(accounts.saveAndFlush(account));
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID id) { return response(require(id)); }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AccountResponse findByEmail(String email) {
        return response(accounts.findByEmail(Account.normalizeEmail(email))
                .orElseThrow(AccountService::notFound));
    }

    @Transactional
    public AccountResponse update(UUID id, AccountRequests.ProfileUpdate input) {
        var account = accounts.findLockedById(id).orElseThrow(AccountService::notFound);
        account.updateProfile(input.firstName(), input.lastName(), input.phone(), clock.instant());
        return response(account);
    }

    @Transactional(readOnly = true)
    public AccountRequests.AddressUpdate address(UUID id) {
        require(id);
        return addresses.findById(id).map(AccountAddress::response).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "Konto nie ma jeszcze adresu."));
    }

    @Transactional
    public AccountRequests.AddressUpdate updateAddress(UUID id, AccountRequests.AddressUpdate input) {
        accounts.findLockedById(id).orElseThrow(AccountService::notFound);
        var address = addresses.findById(id).orElseGet(() -> new AccountAddress(id));
        address.update(input);
        return addresses.saveAndFlush(address).response();
    }

    private Account require(UUID id) { return accounts.findById(id).orElseThrow(AccountService::notFound); }
    static AccountResponse response(Account account) {
        return new AccountResponse(account.id(), account.email(), account.firstName(),
                account.lastName(), account.phone(), account.role(), account.status().name());
    }
    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Nie znaleziono konta.");
    }
}
