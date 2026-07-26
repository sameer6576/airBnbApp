package com.sameerahmed.projects.airBnbApp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Login credentials")
public class LoginDto {
    @NotBlank
    @Email
    @Schema(example = "manager@example.com")
    private String email;

    @NotBlank
    @Schema(example = "Manager@123")
    private String password;
}
