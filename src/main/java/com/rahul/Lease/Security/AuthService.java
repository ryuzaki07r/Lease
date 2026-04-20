package com.rahul.Lease.Security;

import com.rahul.Lease.Dto.LoginDTO;
import com.rahul.Lease.Dto.SignupRequestDTO;
import com.rahul.Lease.Dto.UserDTO;
import com.rahul.Lease.Enums.Role;
import com.rahul.Lease.Exceptions.ResourceNotFoundException;
import com.rahul.Lease.Models.User;
import com.rahul.Lease.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final JWTService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public UserDTO signUp(SignupRequestDTO signupRequestDTO){
        User user = userRepository.findByEmail(signupRequestDTO.getEmail()).orElse(null);
        if(user != null){
            throw new RuntimeException("User is already registered with this email id");
        }
        User newUser = modelMapper.map(signupRequestDTO, User.class);
        newUser.setRoles(Set.of(Role.GUEST));
        newUser.setPassword(passwordEncoder.encode(signupRequestDTO.getPassword()));
        newUser = userRepository.save(newUser);
        return modelMapper.map(newUser, UserDTO.class);
    }

    public String[] login(LoginDTO loginDTO){
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDTO.getEmail(), loginDTO.getPassword()));
        User user = (User) authentication.getPrincipal();
        if(user == null){
            throw new UsernameNotFoundException("Email is not registered, please signUp");
        }
        String[] arr = new String[2];
        arr[0] = jwtService.getAccessToken(user);
        arr[1] = jwtService.generateRefreshToken(user);

        return arr;
    }

    public String refreshToken(String refreshToken){
        Long id  = jwtService.getUserIdFromToken(refreshToken);

        User user = userRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("No user found with id: " +id));
        return jwtService.getAccessToken(user);
    }
}
