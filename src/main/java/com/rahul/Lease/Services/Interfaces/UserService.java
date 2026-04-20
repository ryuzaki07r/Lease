package com.rahul.Lease.Services.Interfaces;

import com.rahul.Lease.Dto.ProfileUpdateRequestDTO;
import com.rahul.Lease.Dto.UserDTO;
import com.rahul.Lease.Models.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public interface UserService {
    User getUserById(Long id);

    void updateProfile(ProfileUpdateRequestDTO profileUpdateRequestDto);

    UserDTO getMyProfile();

    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
}
