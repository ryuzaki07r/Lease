package com.rahul.Lease.Controller;

import com.rahul.Lease.Services.Interfaces.BookingService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Tag(name = "Webhook", description = "Handle webhook events for stripe payment")
public class WebhookController {

    private final BookingService bookingService;
    @Value("${stripe.webhook.secret}")
    private String endPointSecret;

    @PostMapping("/payments")
    @Operation(summary = "Capture Payment", tags = {"Webhook"})
    public ResponseEntity<Void> capturePayments(@RequestBody String payload,
                                                @RequestHeader("Stripe-Signature") String sigHeader){
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, endPointSecret);
        } catch (SignatureVerificationException | RuntimeException e) {
            // Not from Stripe (or tampered with): reject without processing
            log.warn("Rejected webhook with invalid signature or payload: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        bookingService.capturePayment(event);
        return ResponseEntity.noContent().build();
    }
}
