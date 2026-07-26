package com.sameerahmed.projects.airBnbApp.dto;

import com.sameerahmed.projects.airBnbApp.entity.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "Guest details. Provide id to attach an existing guest, or name/gender/age to create one.")
public class GuestDto {
    private Long id;

    @Schema(example = "Alice Guest")
    private String name;

    private Gender gender;

    @Positive
    private Integer age;
}
