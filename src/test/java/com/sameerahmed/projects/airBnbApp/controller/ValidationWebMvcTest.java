package com.sameerahmed.projects.airBnbApp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sameerahmed.projects.airBnbApp.advice.GlobalExceptionHandler;
import com.sameerahmed.projects.airBnbApp.dto.HotelSearchRequest;
import com.sameerahmed.projects.airBnbApp.dto.LoginDto;
import com.sameerahmed.projects.airBnbApp.dto.SignUpRequestDto;
import com.sameerahmed.projects.airBnbApp.security.AuthService;
import com.sameerahmed.projects.airBnbApp.security.JWTAuthFilter;
import com.sameerahmed.projects.airBnbApp.service.HotelService;
import com.sameerahmed.projects.airBnbApp.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, HotelBrowseController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ValidationWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private InventoryService inventoryService;

    @MockitoBean
    private HotelService hotelService;

    @MockitoBean
    private JWTAuthFilter jwtAuthFilter;

    @Test
    void signupRejectsInvalidEmail() throws Exception {
        SignUpRequestDto dto = new SignUpRequestDto();
        dto.setEmail("not-an-email");
        dto.setPassword("password123");

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Validation failed"));
    }

    @Test
    void loginRejectsBlankPassword() throws Exception {
        LoginDto dto = new LoginDto();
        dto.setEmail("user@example.com");
        dto.setPassword(" ");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchRejectsInvalidDateRange() throws Exception {
        HotelSearchRequest request = new HotelSearchRequest();
        request.setCity("New York");
        request.setStartDate(LocalDate.of(2026, 8, 12));
        request.setEndDate(LocalDate.of(2026, 8, 10));
        request.setRoomsCount(1);

        mockMvc.perform(post("/hotels/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Validation failed"));
    }

    @Test
    void searchRejectsMissingCity() throws Exception {
        HotelSearchRequest request = new HotelSearchRequest();
        request.setStartDate(LocalDate.of(2026, 8, 10));
        request.setEndDate(LocalDate.of(2026, 8, 12));
        request.setRoomsCount(1);

        mockMvc.perform(post("/hotels/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
