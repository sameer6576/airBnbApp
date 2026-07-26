package com.sameerahmed.projects.airBnbApp.service;

import com.sameerahmed.projects.airBnbApp.dto.UserDto;
import com.sameerahmed.projects.airBnbApp.dto.UserProfileUpdateDto;
import com.sameerahmed.projects.airBnbApp.entity.User;
import com.sameerahmed.projects.airBnbApp.entity.enums.Role;
import com.sameerahmed.projects.airBnbApp.exception.ResourceNotFoundException;
import com.sameerahmed.projects.airBnbApp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

import static com.sameerahmed.projects.airBnbApp.util.AppUtils.getCurrentUser;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService, UserDetailsService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found with id" + id));
    }

    @Override
    public void updateProfile(UserProfileUpdateDto userProfileUpdateDto) {
        User user = getCurrentUser();

        if (userProfileUpdateDto.getDateOfBirth() != null) {
            user.setDateOfBirth(userProfileUpdateDto.getDateOfBirth());
        }

        if (userProfileUpdateDto.getGender() != null) {
            user.setGender(userProfileUpdateDto.getGender());
        }

        if (userProfileUpdateDto.getName() != null) {
            user.setName(userProfileUpdateDto.getName());
        }
        userRepository.save(user);
    }

    @Override
    @Transactional
    public UserDto promoteToHotelManager(Long userId) {
        User user = getUserById(userId);
        if (user.getRoles() == null) {
            user.setRoles(new HashSet<>());
        }
        user.getRoles().add(Role.HOTEL_MANAGER);
        user.getRoles().add(Role.GUEST);
        user = userRepository.save(user);
        return modelMapper.map(user, UserDto.class);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
    }
}
