package com.sameerahmed.projects.airBnbApp.dto;

import lombok.Data;

@Data
public class SignUpRequestDto {
    private String email;
    private String password;
    private Long id;
}
