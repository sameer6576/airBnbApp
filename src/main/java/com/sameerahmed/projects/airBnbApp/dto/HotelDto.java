package com.sameerahmed.projects.airBnbApp.dto;

import com.sameerahmed.projects.airBnbApp.entity.HotelContactInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Hotel details for admin create/update")
public class HotelDto {
    private Long id;

    @NotBlank
    @Schema(example = "Skyline Suites")
    private String name;

    @NotBlank
    @Schema(example = "New York")
    private String city;

    private String[] photos;
    private String[] amenities;
    private HotelContactInfo contactInfo;
    private Boolean active;
}
