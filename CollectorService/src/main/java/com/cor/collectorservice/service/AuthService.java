package com.cor.collectorservice.service;

import com.cor.collectorservice.dto.auth.LoginRequest;
import com.cor.collectorservice.dto.auth.RegisterRequest;
import com.cor.collectorservice.dto.auth.UserResponse;
import com.cor.collectorservice.entity.User;
import com.cor.collectorservice.mapper.UserMapper;
import com.cor.collectorservice.repository.UserRepository;
import com.cor.collectorservice.util.EncryptionUtil;
import com.cor.collectorservice.util.exception.InvalidCredentialsException;
import com.cor.collectorservice.util.exception.UserAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    AuthenticationManager authenticationManager;
    SecurityContextRepository securityContextRepository;
    EncryptionUtil encryptionUtil;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.info("Начало регистрации пользователя с именем: {}", request.getUsername());
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Попытка регистрации с существующим именем пользователя: {}", request.getUsername());
            throw new UserAlreadyExistsException(request.getUsername());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        if (StringUtils.hasText(request.getWbToken())) {
            log.debug("Шифрование WB-токена для пользователя: {}", request.getUsername());
            user.setWbToken(encryptionUtil.encrypt(request.getWbToken()));
        }

        User savedUser = userRepository.save(user);
        log.info("Пользователь успешно зарегистрирован с ID: {}", savedUser.getId());
        return userMapper.toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        log.info("Попытка входа пользователя с именем: {}", request.getUsername());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            log.info("Успешная аутентификация пользователя: {}", request.getUsername());
        } catch (Exception ex) {
            log.warn("Неудачная попытка входа пользователя: {}", request.getUsername());
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException());

        log.info("Пользователь успешно вошел в систему с ID: {}", user.getId());
        return userMapper.toResponse(user);
    }
}
