package com.shop.tire.order;

import java.time.Clock;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderMailDelivery {
    private static final Logger log=LoggerFactory.getLogger(OrderMailDelivery.class);
    private final OrderMailOutbox outbox;
    private final TrackingRepository links;
    private final TrackingSecrets secrets;
    private final ObjectMapper json;
    private final JavaMailSender sender;
    private final Clock clock;
    private final String from;

    OrderMailDelivery(OrderMailOutbox outbox, TrackingRepository links, TrackingSecrets secrets, ObjectMapper json,
                      JavaMailSender sender, Clock clock, @Value("${app.account.mail-from}") String from) {
        this.outbox=outbox; this.links=links; this.secrets=secrets; this.json=json;
        this.sender=sender; this.clock=clock; this.from=from;
    }

    @Transactional
    public boolean sendNext() {
        var next=outbox.next(clock.instant());
        if (next.isEmpty()) return false;
        var item=next.get();
        if (!clock.instant().isBefore(item.expiresAt())) {
            outbox.failed(item,clock.instant(),true);
            return true;
        }
        OrderTrackingService.MailContent content;
        try {
            content=json.readValue(secrets.decrypt(item.payload()),OrderTrackingService.MailContent.class);
        } catch (RuntimeException ex) {
            outbox.failed(item,clock.instant(),true);
            log.error("Order email payload unavailable; job {}",item.id());
            return true;
        }
        var message=new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(content.email());
        message.setSubject("TireShop — śledzenie zamówienia "+content.orderNumber());
        message.setText("Zamówienie "+content.orderNumber()+" zostało opłacone.\n"
                +"Status oraz numer przesyłki sprawdzisz pod linkiem:\n"+content.link()
                +"\nLink jest ważny do "+content.expiresAt()+". Nie udostępniaj go innym osobom.");
        try {
            sender.send(message);
        } catch (MailException ex) {
            outbox.failed(item,clock.instant(),false);
            // SMTP exceptions may include recipients or message contents; never log the exception.
            log.warn("Order email delivery failed; job {}, attempt {}",item.id(),item.attempts()+1);
            return true;
        }
        outbox.sent(item.id(),clock.instant());
        return true;
    }

    @Transactional
    public void cleanExpiredSecrets() { links.clean(clock.instant()); }
}
