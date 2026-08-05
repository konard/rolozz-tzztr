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
import com.cor.collectorservice.util.exception.InvalidCredentialsException;
import com.cor.collectorservice.util.exception.KeycloakUnavailableException;
import com.cor.collectorservice.util.exception.RegistrationFailedException;
import com.cor.collectorservice.util.exception.UserAlreadyExistsException;
import com.cor.collectorservice.util.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Тесты саги регистрации и входа через Keycloak.
 * <p>
 * Проверяют требования задачи: сначала регистрация в Keycloak, затем — в базе данных сервиса
 * с идентификатором, полученным от Keycloak; если запись в базу откатилась,
 * учётная запись Keycloak удаляется компенсацией.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private KeycloakAdminClient keycloakAdminClient;
    @Mock
    private KeycloakTokenClient keycloakTokenClient;
    @Mock
    private UserProfileRegistrar userProfileRegistrar;
    @Mock
    private RegistrationCompensationService compensationService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("john_doe");
        registerRequest.setPassword("password123");
        registerRequest.setEmail("john_doe@example.com");
    }

    @Test
    @DisplayName("Регистрация: сначала Keycloak, затем профиль в БД с идентификатором из Keycloak")
    void registersInKeycloakFirstAndThenInDatabase() {
        UUID keycloakUserId = UUID.randomUUID();
        User savedUser = new User();
        savedUser.setId(keycloakUserId);
        savedUser.setUsername("john_doe");
        UserResponse expected = UserResponse.builder().id(keycloakUserId).username("john_doe").build();

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(keycloakAdminClient.createUser("john_doe", "john_doe@example.com", "password123"))
                .thenReturn(keycloakUserId);
        when(userProfileRegistrar.createProfile(keycloakUserId, registerRequest)).thenReturn(savedUser);
        when(userMapper.toResponse(savedUser)).thenReturn(expected);

        UserResponse result = authService.register(registerRequest);

        assertThat(result).isSameAs(expected);
        assertThat(result.getId()).isEqualTo(keycloakUserId);

        // Порядок шагов саги важен: учётная запись в Keycloak создаётся раньше профиля в БД
        InOrder order = inOrder(keycloakAdminClient, userProfileRegistrar);
        order.verify(keycloakAdminClient).createUser("john_doe", "john_doe@example.com", "password123");
        order.verify(userProfileRegistrar).createProfile(keycloakUserId, registerRequest);

        verifyNoInteractions(compensationService);
    }

    @Test
    @DisplayName("Регистрация: занятый логин отклоняется до обращения к Keycloak")
    void rejectsDuplicateUsernameBeforeCallingKeycloak() {
        when(userRepository.existsByUsername("john_doe")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("john_doe");

        verifyNoInteractions(keycloakAdminClient, userProfileRegistrar, compensationService);
    }

    @Test
    @DisplayName("Регистрация: ошибка Keycloak прерывает сагу, профиль в БД не создаётся")
    void doesNotTouchDatabaseWhenKeycloakRegistrationFails() {
        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(keycloakAdminClient.createUser(anyString(), anyString(), anyString()))
                .thenThrow(new KeycloakUnavailableException("Keycloak недоступен"));

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(KeycloakUnavailableException.class);

        verifyNoInteractions(userProfileRegistrar, compensationService);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Регистрация: откат записи в БД запускает компенсацию учётной записи Keycloak")
    void compensatesKeycloakAccountWhenDatabaseWriteRollsBack() {
        UUID keycloakUserId = UUID.randomUUID();
        RuntimeException databaseFailure = new IllegalStateException("Транзакция откатилась");

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(keycloakAdminClient.createUser("john_doe", "john_doe@example.com", "password123"))
                .thenReturn(keycloakUserId);
        when(userProfileRegistrar.createProfile(keycloakUserId, registerRequest)).thenThrow(databaseFailure);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(RegistrationFailedException.class)
                .hasMessageContaining("john_doe")
                .hasCause(databaseFailure);

        verify(compensationService).compensate(keycloakUserId, "john_doe");
    }

    @Test
    @DisplayName("Вход: токены Keycloak возвращаются вместе с профилем пользователя")
    void loginReturnsTokensAndProfile() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("john_doe");
        loginRequest.setPassword("password123");

        TokenResponse tokens = new TokenResponse("access", 300L, "refresh", 1800L, "Bearer", "profile email");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("john_doe");
        UserResponse profile = UserResponse.builder().id(user.getId()).username("john_doe").build();

        when(keycloakTokenClient.obtainToken("john_doe", "password123")).thenReturn(tokens);
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(profile);

        AuthResponse response = authService.login(loginRequest);

        assertThat(response.tokens()).isSameAs(tokens);
        assertThat(response.user()).isSameAs(profile);
    }

    @Test
    @DisplayName("Вход: неверные учётные данные отклоняет Keycloak, база данных не запрашивается")
    void loginPropagatesInvalidCredentials() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("john_doe");
        loginRequest.setPassword("wrong");

        when(keycloakTokenClient.obtainToken("john_doe", "wrong"))
                .thenThrow(new InvalidCredentialsException());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(userMapper);
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    @DisplayName("Вход: учётная запись есть в Keycloak, но профиля в базе нет")
    void loginFailsWhenProfileIsMissing() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("orphan");
        loginRequest.setPassword("password123");

        when(keycloakTokenClient.obtainToken("orphan", "password123"))
                .thenReturn(new TokenResponse("access", 300L, "refresh", 1800L, "Bearer", null));
        when(userRepository.findByUsername("orphan")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Обновление и выход делегируются Keycloak")
    void refreshAndLogoutAreDelegatedToKeycloak() {
        TokenResponse tokens = new TokenResponse("new-access", 300L, "new-refresh", 1800L, "Bearer", null);
        when(keycloakTokenClient.refreshToken("refresh")).thenReturn(tokens);

        assertThat(authService.refresh("refresh")).isSameAs(tokens);

        authService.logout("refresh");
        verify(keycloakTokenClient).logout("refresh");
    }
}
