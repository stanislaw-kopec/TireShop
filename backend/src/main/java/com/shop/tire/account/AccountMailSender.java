package com.shop.tire.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
class AccountMailSender {
    private final JavaMailSender sender;
    private final String from;
    private final String linkUrl;

    AccountMailSender(JavaMailSender sender, @Value("${app.account.mail-from}") String from,
                      @Value("${app.account.password-link-url}") String linkUrl) {
        this.sender = sender;
        this.from = from;
        this.linkUrl = linkUrl;
    }

    void send(String email, String token, boolean invitation) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(invitation ? "TireShop — zaproszenie pracownika" : "TireShop — reset hasła");
        var link = UriComponentsBuilder.fromUriString(linkUrl).fragment("token=" + token).build().toUriString();
        message.setText("Ustaw hasło, korzystając z jednorazowego linku:\n" + link
                + "\nJeśli wiadomość Cię nie dotyczy, zignoruj ją.");
        sender.send(message);
    }
}
