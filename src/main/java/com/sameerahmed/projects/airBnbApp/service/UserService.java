package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.UserDto;
import com.sameerahmed.projects.airBnbApp.dto.UserProfileUpdateDto;
import com.sameerahmed.projects.airBnbApp.entity.User;

public interface UserService {

    User getUserById(Long id);

    void updateProfile(UserProfileUpdateDto userProfileUpdateDto);

    UserDto promoteToHotelManager(Long userId);
}
