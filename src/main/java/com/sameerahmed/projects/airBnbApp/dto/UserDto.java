package com.sameerahmed.projects.airBnbApp.dto;

import com.sameerahmed.projects.airBnbApp.entity.enums.Gender;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UserDto {
    private Long id;
    private String email;
    private String name;
    private Gender gender;
    private LocalDate dateOfBirth;;

}
