package com.aijob.server.controller;

import com.aijob.server.dto.LoginRequest;
import com.aijob.server.dto.RegisterRequest;
import com.aijob.server.service.UserService;
import com.aijob.server.vo.LoginVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public String register(
            @Valid @RequestBody RegisterRequest request
    ) {
        userService.register(request);
        return "注册成功";
    }

    @PostMapping("/login")
    public LoginVO login(
            @Valid @RequestBody LoginRequest request
    ) {
        return userService.login(request);
    }
}
