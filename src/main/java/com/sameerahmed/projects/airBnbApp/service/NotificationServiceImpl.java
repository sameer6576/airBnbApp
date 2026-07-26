package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.entity.Booking;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@airbnbapp.local}")
    private String fromAddress;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Override
    public void sendBookingConfirmed(Booking booking) {
        String subject = "Booking confirmed #" + booking.getId();
        String body = """
                Your booking at %s is confirmed.
                Check-in: %s
                Check-out: %s
                Amount: %s
                """.formatted(
                booking.getHotel().getName(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getAmount()
        );
        dispatch(booking.getUser().getEmail(), subject, body);
    }

    @Override
    public void sendBookingCancelled(Booking booking, BigDecimal refundAmount) {
        String subject = "Booking cancelled #" + booking.getId();
        String body = """
                Your booking at %s was cancelled.
                Refund amount: %s
                """.formatted(booking.getHotel().getName(), refundAmount);
        dispatch(booking.getUser().getEmail(), subject, body);
    }

    @Override
    public void sendBookingExpiryWarning(Booking booking) {
        String subject = "Booking reservation expiring soon #" + booking.getId();
        String body = """
                Your temporary reservation at %s will expire soon.
                Complete payment to keep booking #%s.
                """.formatted(booking.getHotel().getName(), booking.getId());
        dispatch(booking.getUser().getEmail(), subject, body);
    }

    private void dispatch(String to, String subject, String body) {
        log.info("Notification to {}: {} | {}", to, subject, body.replace('\n', ' '));
        if (!mailEnabled || mailSender == null) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
