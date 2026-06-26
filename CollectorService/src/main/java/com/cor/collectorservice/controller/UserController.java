package com.cor.collectorservice.controller;

import com.cor.collectorservice.dto.UpdateUserRequest;
import com.cor.collectorservice.dto.UserResponse;
import com.cor.collectorservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Users", description = "Операции с текущим пользователем")
@SecurityRequirement(name = "sessionAuth")
public class UserController {

    UserService userService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Получить данные текущего пользователя")
    public UserResponse getCurrentUser() {
        return userService.getCurrentUser();
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Обновить данные текущего пользователя")
    public UserResponse updateCurrentUser(@Valid @RequestBody UpdateUserRequest request) {
        return userService.updateCurrentUser(request);
    }

    @DeleteMapping("/me")
    @PreAuthorize("hasRole('USER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить текущего пользователя")
    public void deleteCurrentUser() {
        userService.deleteCurrentUser();
    }
    
}
