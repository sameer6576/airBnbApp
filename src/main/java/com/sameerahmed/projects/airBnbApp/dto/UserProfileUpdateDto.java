package com.sameerahmed.projects.airBnbApp.dto;

import com.sameerahmed.projects.airBnbApp.entity.enums.Gender;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UserProfileUpdateDto {
    private String name;
    private LocalDate dateOfBirth;
    private Gender gender;
}
