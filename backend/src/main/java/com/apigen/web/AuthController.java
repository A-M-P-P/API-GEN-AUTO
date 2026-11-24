package com.apigen.web;

import com.apigen.dto.UserDtos;
import com.apigen.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public UserDtos.LoginResponse login(@Valid @RequestBody UserDtos.LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserDtos.UserResponse me() {
        return authService.me();
    }

    @PostMapping("/change-password")
    public UserDtos.UserResponse changePassword(@Valid @RequestBody UserDtos.ChangePasswordRequest request) {
        return authService.changePassword(request);
    }
}
