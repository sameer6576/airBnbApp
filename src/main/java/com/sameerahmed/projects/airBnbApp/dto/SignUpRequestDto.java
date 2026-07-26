package com.sameerahmed.projects.airBnbApp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "User registration request")
public class SignUpRequestDto {
    @NotBlank
    @Email
    @Schema(example = "guest@example.com")
    private String email;

    @NotBlank
    @Size(min = 6, max = 100)
    @Schema(example = "password123")
    private String password;

    private Long id;
}
