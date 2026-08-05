package com.cor.collectorservice.service;

import com.cor.collectorservice.dto.auth.RegisterRequest;
import com.cor.collectorservice.entity.User;
import com.cor.collectorservice.repository.UserRepository;
import com.cor.collectorservice.util.EncryptionUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Сохранение профиля пользователя в базе данных сервиса на втором шаге регистрации.
 * <p>
 * Вынесено в отдельный бин намеренно: метод помечен
 * {@link Propagation#REQUIRES_NEW}, и вызов из {@link AuthService} проходит через прокси,
 * поэтому транзакция гарантированно завершается (фиксируется или откатывается) до того,
 * как {@code AuthService} начнёт компенсацию — удаление учётной записи из Keycloak.
 * Если бы метод жил в самом {@code AuthService}, вызов был бы внутренним,
 * аннотация не сработала бы, и компенсация запускалась бы внутри ещё не откаченной транзакции.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileRegistrar {

    UserRepository userRepository;
    EncryptionUtil encryptionUtil;

    /**
     * Создаёт профиль пользователя, идентификатор которого равен идентификатору учётной записи Keycloak.
     *
     * @param keycloakUserId идентификатор учётной записи, созданной в Keycloak
     * @param request        данные регистрации
     * @return сохранённый профиль пользователя
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User createProfile(UUID keycloakUserId, RegisterRequest request) {
        log.info("Сохранение профиля пользователя {} с идентификатором Keycloak {}",
                request.getUsername(), keycloakUserId);

        User user = new User();
        user.setId(keycloakUserId);
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());

        if (StringUtils.hasText(request.getWbToken())) {
            log.debug("Шифрование WB-токена пользователя {}", request.getUsername());
            user.setWbToken(encryptionUtil.encrypt(request.getWbToken()));
        }

        User savedUser = userRepository.saveAndFlush(user);
        log.info("Профиль пользователя {} сохранён в базе данных, идентификатор {}",
                savedUser.getUsername(), savedUser.getId());
        return savedUser;
    }
}
