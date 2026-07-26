package com.sameerahmed.projects.airBnbApp.dto;

import com.sameerahmed.projects.airBnbApp.entity.enums.Gender;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class GuestDto {
    private Long id;
    private String name;
    private Gender gender;

    @Positive
    private Integer age;
}
