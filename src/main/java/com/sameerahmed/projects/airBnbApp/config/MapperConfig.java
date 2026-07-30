package com.sameerahmed.projects.airBnbApp.config;

import com.sameerahmed.projects.airBnbApp.dto.HotelDto;
import com.sameerahmed.projects.airBnbApp.dto.SignUpRequestDto;
import com.sameerahmed.projects.airBnbApp.entity.Hotel;
import com.sameerahmed.projects.airBnbApp.entity.User;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapperConfig {

    @Bean
    public ModelMapper modelmapper() {
        ModelMapper modelMapper = new ModelMapper();

        // Request DTOs must never be able to drive an entity's identifier.
        // Spring Data's save() calls merge() rather than persist() when an id is
        // present, so a client-supplied id silently turns an insert into an
        // overwrite of somebody else's row.
        modelMapper.typeMap(SignUpRequestDto.class, User.class)
                .addMappings(mapper -> mapper.skip(User::setId));

        // Server-owned fields on an existing hotel. These are set by
        // activateHotel and by the review aggregation, never by the caller.
        modelMapper.typeMap(HotelDto.class, Hotel.class)
                .addMappings(mapper -> {
                    mapper.skip(Hotel::setId);
                    mapper.skip(Hotel::setActive);
                    mapper.skip(Hotel::setAverageRating);
                    mapper.skip(Hotel::setReviewCount);
                    mapper.skip(Hotel::setOwner);
                });

        return modelMapper;
    }
}
