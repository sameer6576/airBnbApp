package com.sameerahmed.projects.airBnbApp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Hotel review")
public class ReviewDto {
    private Long id;
    private Long hotelId;
    private Long bookingId;
    private String userName;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    @Size(max = 2000)
    private String comment;

    private LocalDateTime createdAt;
}
