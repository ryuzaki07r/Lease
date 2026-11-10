package com.rahul.Lease.Services.Interfaces;

import com.rahul.Lease.Dto.ProfileUpdateRequestDTO;
import com.rahul.Lease.Dto.UserDTO;
import com.rahul.Lease.Models.User;
import org.springframework.security.core.userdetails.UserDetailsService;

// Extends UserDetailsService so Spring Security can load users during login
public interface UserService extends UserDetailsService {
    User getUserById(Long id);

    void updateProfile(ProfileUpdateRequestDTO profileUpdateRequestDto);

    UserDTO getMyProfile();
}
