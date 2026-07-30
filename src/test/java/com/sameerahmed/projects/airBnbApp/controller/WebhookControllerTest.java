package com.sameerahmed.projects.airBnbApp.controller;

import com.sameerahmed.projects.airBnbApp.advice.GlobalExceptionHandler;
import com.sameerahmed.projects.airBnbApp.security.JWTAuthFilter;
import com.sameerahmed.projects.airBnbApp.service.BookingService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "stripe.webhook.secret=whsec_test")
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private JWTAuthFilter jwtAuthFilter;

    @Test
    void invalidSignatureReturnsBadRequest() throws Exception {
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(any(), any(), eq("whsec_test")))
                    .thenThrow(mock(SignatureVerificationException.class));

            mockMvc.perform(post("/webhook/payment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "bad")
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void validEventDelegatesToBookingService() throws Exception {
        Event event = mock(Event.class);
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(any(), any(), eq("whsec_test")))
                    .thenReturn(event);

            mockMvc.perform(post("/webhook/payment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Stripe-Signature", "good")
                            .content("{\"id\":\"evt_1\"}"))
                    .andExpect(status().isNoContent());

            verify(bookingService).capturePayment(event);
        }
    }
}
