package com.cor.collectorservice.controller;

import com.cor.collectorservice.dto.auth.LoginRequest;
import com.cor.collectorservice.dto.auth.RegisterRequest;
import com.cor.collectorservice.dto.auth.UserResponse;
import com.cor.collectorservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Authentication", description = "Регистрация и вход")
public class AuthController {

    AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Регистрация нового пользователя")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST запрос на регистрацию пользователя: {}", request.getUsername());
        UserResponse response = authService.register(request);
        log.info("Пользователь успешно зарегистрирован: {}", response.getUsername());
        return response;
    }

    @PostMapping("/login")
    @Operation(summary = "Вход в систему")
    public UserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        log.info("POST запрос на вход пользователя: {}", request.getUsername());
        UserResponse response = authService.login(request, httpRequest, httpResponse);
        log.info("Пользователь успешно вошел в систему: {}", response.getUsername());
        return response;
    }
}
