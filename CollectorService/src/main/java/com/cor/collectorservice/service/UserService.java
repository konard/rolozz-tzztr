package com.cor.collectorservice.service;

import com.cor.collectorservice.dto.UpdateUserRequest;
import com.cor.collectorservice.dto.UserResponse;
import com.cor.collectorservice.entity.User;
import com.cor.collectorservice.mapper.UserMapper;
import com.cor.collectorservice.repository.UserRepository;
import com.cor.collectorservice.util.exception.BadRequestException;
import com.cor.collectorservice.util.exception.UnauthorizedAccessException;
import com.cor.collectorservice.util.exception.UserAlreadyExistsException;
import com.cor.collectorservice.util.exception.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {

    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        User user = getAuthenticatedUser();
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        if (!StringUtils.hasText(request.getUsername()) && !StringUtils.hasText(request.getPassword())) {
            throw new BadRequestException("At least one field must be provided for update");
        }

        User user = getAuthenticatedUser();

        if (StringUtils.hasText(request.getUsername()) && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new UserAlreadyExistsException(request.getUsername());
            }
            user.setUsername(request.getUsername());
        }

        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User updatedUser = userRepository.save(user);
        return userMapper.toResponse(updatedUser);
    }

    @Transactional
    public void deleteCurrentUser() {
        User user = getAuthenticatedUser();
        userRepository.delete(user);
        SecurityContextHolder.clearContext();
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedAccessException();
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
    }
}
