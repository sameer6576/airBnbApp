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
        dispatch(booking.getUser().getEmail(),
                "Booking confirmed #" + booking.getId(),
                "Confirmed at " + booking.getHotel().getName()
                        + " (" + booking.getCheckInDate() + " → " + booking.getCheckOutDate()
                        + "), amount " + booking.getAmount());
    }

    @Override
    public void sendBookingCancelled(Booking booking, BigDecimal refundAmount) {
        dispatch(booking.getUser().getEmail(),
                "Booking cancelled #" + booking.getId(),
                "Cancelled " + booking.getHotel().getName() + ", refund " + refundAmount);
    }

    @Override
    public void sendBookingExpiryWarning(Booking booking) {
        dispatch(booking.getUser().getEmail(),
                "Reservation expiring #" + booking.getId(),
                "Pay soon to keep booking at " + booking.getHotel().getName());
    }

    private void dispatch(String to, String subject, String body) {
        log.info("Notification to {}: {} | {}", to, subject, body);
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
