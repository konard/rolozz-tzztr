package com.cor.collectorservice.service;

import com.cor.collectorservice.dto.auth.UpdateUserRequest;
import com.cor.collectorservice.dto.auth.UserResponse;
import com.cor.collectorservice.entity.User;
import com.cor.collectorservice.mapper.UserMapper;
import com.cor.collectorservice.repository.UserRepository;
import com.cor.collectorservice.util.EncryptionUtil;
import com.cor.collectorservice.util.exception.BadRequestException;
import com.cor.collectorservice.util.exception.UnauthorizedAccessException;
import com.cor.collectorservice.util.exception.UserAlreadyExistsException;
import com.cor.collectorservice.util.exception.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {

    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    EncryptionUtil encryptionUtil;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        log.debug("Получение данных текущего пользователя");
        User user = getAuthenticatedUser();
        log.info("Данные пользователя получены: {}", user.getUsername());
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        log.info("Начало обновления данных пользователя");
        if (!StringUtils.hasText(request.getUsername()) && !StringUtils.hasText(request.getPassword())
                && !StringUtils.hasText(request.getWbToken())) {
            log.warn("Попытка обновления без указания полей");
            throw new BadRequestException("Хотя бы одно поле должно быть указано для обновления");
        }

        User user = getAuthenticatedUser();
        log.debug("Обновление пользователя: {}", user.getUsername());

        if (StringUtils.hasText(request.getUsername()) && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                log.warn("Попытка смены имени на уже существующее: {}", request.getUsername());
                throw new UserAlreadyExistsException(request.getUsername());
            }
            log.info("Смена имени пользователя с {} на {}", user.getUsername(), request.getUsername());
            user.setUsername(request.getUsername());
        }

        if (StringUtils.hasText(request.getPassword())) {
            log.debug("Обновление пароля пользователя: {}", user.getUsername());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (StringUtils.hasText(request.getWbToken())) {
            log.debug("Обновление WB-токена пользователя: {}", user.getUsername());
            user.setWbToken(encryptionUtil.encrypt(request.getWbToken()));
        }

        User updatedUser = userRepository.save(user);
        log.info("Данные пользователя успешно обновлены: {}", updatedUser.getUsername());
        return userMapper.toResponse(updatedUser);
    }

    @Transactional
    public void deleteCurrentUser() {
        log.info("Начало удаления текущего пользователя");
        User user = getAuthenticatedUser();
        log.warn("Удаление пользователя: {}", user.getUsername());
        userRepository.delete(user);
        SecurityContextHolder.clearContext();
        log.info("Пользователь успешно удален: {}", user.getUsername());
    }

    @Transactional(readOnly = true)
    public String getDecryptedWbToken() {
        log.debug("Получение расшифрованного WB-токена");
        User user = getAuthenticatedUser();
        if (user.getWbToken() == null) {
            log.debug("WB-токен не установлен для пользователя: {}", user.getUsername());
            return null;
        }
        String decryptedToken = encryptionUtil.decrypt(user.getWbToken());
        if (decryptedToken != null && decryptedToken.startsWith("\"") && decryptedToken.endsWith("\"")) {
            decryptedToken = decryptedToken.substring(1, decryptedToken.length() - 1);
        }
        log.info("WB-токен успешно расшифрован для пользователя: {}", user.getUsername());
        return decryptedToken;
    }

    User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            log.warn("Попытка доступа без аутентификации");
            throw new UnauthorizedAccessException();
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("Аутентифицированный пользователь не найден в базе: {}", username);
                    return new UserNotFoundException(username);
                });
    }
}
