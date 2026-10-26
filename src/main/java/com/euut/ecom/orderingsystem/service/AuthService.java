package com.euut.ecom.orderingsystem.service;

import com.euut.ecom.orderingsystem.dto.AuthResponseDto;
import com.euut.ecom.orderingsystem.dto.LoginRequestDto;
import com.euut.ecom.orderingsystem.dto.RegisterRequestDto;
import com.euut.ecom.orderingsystem.entity.User;
import com.euut.ecom.orderingsystem.repository.UserRepository;
import com.euut.ecom.orderingsystem.security.JwtService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User savedUser = userRepository.save(user);

        String token = jwtService.generateToken(savedUser.getEmail(), savedUser.getId());
        return new AuthResponseDto(token);
    }

    @Transactional(readOnly = true)
    public AuthResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return new AuthResponseDto(token);
    }
}