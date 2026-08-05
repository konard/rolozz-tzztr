package com.cor.collectorservice.service;

import com.cor.collectorservice.client.KeycloakAdminClient;
import com.cor.collectorservice.client.KeycloakTokenClient;
import com.cor.collectorservice.dto.auth.AuthResponse;
import com.cor.collectorservice.dto.auth.LoginRequest;
import com.cor.collectorservice.dto.auth.RegisterRequest;
import com.cor.collectorservice.dto.auth.TokenResponse;
import com.cor.collectorservice.dto.auth.UserResponse;
import com.cor.collectorservice.entity.User;
import com.cor.collectorservice.mapper.UserMapper;
import com.cor.collectorservice.repository.UserRepository;
import com.cor.collectorservice.util.exception.RegistrationFailedException;
import com.cor.collectorservice.util.exception.UserAlreadyExistsException;
import com.cor.collectorservice.util.exception.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Регистрация и вход пользователей через Keycloak.
 * <p>
 * Сервис не хранит пароли и не проверяет их самостоятельно: учётными данными управляет Keycloak,
 * а в базе данных сервиса лежит только профиль пользователя, первичный ключ которого совпадает
 * с идентификатором учётной записи в Keycloak.
 * <p>
 * <b>Порядок регистрации (сага с компенсацией):</b>
 * <ol>
 *     <li>проверка, что логин ещё не занят в базе сервиса — дешёвый отказ до обращения к Keycloak;</li>
 *     <li>создание учётной записи в Keycloak и назначение realm-роли;</li>
 *     <li>сохранение профиля в базе сервиса в отдельной транзакции
 *         ({@link UserProfileRegistrar}) с идентификатором из Keycloak;</li>
 *     <li>если транзакция откатилась — компенсация: удаление учётной записи из Keycloak,
 *         а при недоступности Keycloak — постановка задачи в очередь повторов
 *         ({@link RegistrationCompensationService}).</li>
 * </ol>
 * Метод регистрации намеренно не помечен {@link Transactional}: транзакция базы данных
 * должна завершиться до запуска компенсации.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    UserRepository userRepository;
    UserMapper userMapper;
    KeycloakAdminClient keycloakAdminClient;
    KeycloakTokenClient keycloakTokenClient;
    UserProfileRegistrar userProfileRegistrar;
    RegistrationCompensationService compensationService;

    /**
     * Регистрирует пользователя: сначала в Keycloak, затем в базе данных сервиса.
     *
     * @param request данные регистрации
     * @return профиль созданного пользователя
     * @throws UserAlreadyExistsException  если логин уже занят в базе сервиса или в Keycloak
     * @throws RegistrationFailedException если учётная запись в Keycloak создана,
     *                                     но профиль в базе сохранить не удалось (запущена компенсация)
     */
    public UserResponse register(RegisterRequest request) {
        String username = request.getUsername();
        log.info("Начало регистрации пользователя {}", username);

        if (userRepository.existsByUsername(username)) {
            log.warn("Регистрация отклонена: логин {} уже занят в базе данных сервиса", username);
            throw new UserAlreadyExistsException(username);
        }

        UUID keycloakUserId = keycloakAdminClient.createUser(username, request.getEmail(), request.getPassword());
        log.info("Шаг 1 регистрации выполнен: пользователь {} создан в Keycloak с идентификатором {}",
                username, keycloakUserId);

        try {
            User savedUser = userProfileRegistrar.createProfile(keycloakUserId, request);
            log.info("Шаг 2 регистрации выполнен: профиль пользователя {} сохранён, регистрация завершена", username);
            return userMapper.toResponse(savedUser);
        } catch (RuntimeException ex) {
            log.error("Шаг 2 регистрации не выполнен: сохранение профиля пользователя {} откатилось. "
                    + "Запускаем компенсацию учётной записи {}", username, keycloakUserId, ex);
            compensationService.compensate(keycloakUserId, username);
            throw new RegistrationFailedException(username, ex);
        }
    }

    /**
     * Выполняет вход: получает токены у Keycloak и возвращает их вместе с профилем пользователя.
     *
     * @param request логин и пароль
     * @return токены Keycloak и профиль пользователя
     * @throws com.cor.collectorservice.util.exception.InvalidCredentialsException если Keycloak отверг учётные данные
     * @throws UserNotFoundException                                               если учётная запись есть в Keycloak,
     *                                                                             но профиля в базе сервиса нет
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String username = request.getUsername();
        log.info("Попытка входа пользователя {}", username);

        TokenResponse tokens = keycloakTokenClient.obtainToken(username, request.getPassword());

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("Учётная запись {} есть в Keycloak, но профиль в базе данных отсутствует. "
                            + "Вероятно, компенсация регистрации не была выполнена", username);
                    return new UserNotFoundException(username);
                });

        log.info("Пользователь {} успешно вошёл в систему, идентификатор {}", username, user.getId());
        return new AuthResponse(tokens, userMapper.toResponse(user));
    }

    /**
     * Продлевает сессию: обменивает refresh-токен на новую пару токенов.
     *
     * @param refreshToken refresh-токен, полученный при входе
     * @return новая пара токенов
     * @throws com.cor.collectorservice.util.exception.InvalidTokenException если refresh-токен недействителен
     */
    public TokenResponse refresh(String refreshToken) {
        log.info("Запрос на обновление пары токенов");
        return keycloakTokenClient.refreshToken(refreshToken);
    }

    /**
     * Завершает сессию пользователя в Keycloak.
     *
     * @param refreshToken refresh-токен, полученный при входе
     */
    public void logout(String refreshToken) {
        log.info("Запрос на выход из системы");
        keycloakTokenClient.logout(refreshToken);
    }
}
