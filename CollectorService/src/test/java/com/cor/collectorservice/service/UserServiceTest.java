package com.cor.collectorservice.service;

import com.cor.collectorservice.client.KeycloakAdminClient;
import com.cor.collectorservice.dto.auth.UpdateUserRequest;
import com.cor.collectorservice.dto.auth.UserResponse;
import com.cor.collectorservice.entity.User;
import com.cor.collectorservice.mapper.UserMapper;
import com.cor.collectorservice.repository.UserRepository;
import com.cor.collectorservice.util.EncryptionUtil;
import com.cor.collectorservice.util.context.UserContext;
import com.cor.collectorservice.util.context.UserContextHolder;
import com.cor.collectorservice.util.exception.BadRequestException;
import com.cor.collectorservice.util.exception.KeycloakUnavailableException;
import com.cor.collectorservice.util.exception.UnauthorizedAccessException;
import com.cor.collectorservice.util.exception.UserAlreadyExistsException;
import com.cor.collectorservice.util.exception.UserNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Тесты работы с профилем пользователя после отказа от Spring Security.
 * <p>
 * Текущий пользователь определяется по {@link UserContextHolder}, учётные данные
 * (логин, e-mail, пароль) синхронизируются с Keycloak.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private EncryptionUtil encryptionUtil;
    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("john_doe");
        user.setEmail("john_doe@example.com");

        UserContextHolder.set(new UserContext(user.getId(), "john_doe", "john_doe@example.com", Set.of("USER")));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("Текущий пользователь ищется по идентификатору из токена, а не по логину")
    void resolvesCurrentUserByTokenSubject() {
        UserResponse expected = UserResponse.builder().id(user.getId()).username("john_doe").build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(expected);

        assertThat(userService.getCurrentUser()).isSameAs(expected);

        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    @DisplayName("Запрос без аутентификации отклоняется с 401")
    void failsWithoutAuthentication() {
        UserContextHolder.clear();

        assertThatThrownBy(() -> userService.getCurrentUser())
                .isInstanceOf(UnauthorizedAccessException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Учётная запись есть в Keycloak, но профиля в базе нет")
    void failsWhenProfileIsMissing() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser())
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Обновление без полей отклоняется до обращения к базе и Keycloak")
    void rejectsEmptyUpdateRequest() {
        assertThatThrownBy(() -> userService.updateCurrentUser(new UpdateUserRequest()))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(userRepository, keycloakAdminClient);
    }

    @Test
    @DisplayName("Смена логина сохраняется в базе и переносится в Keycloak")
    void propagatesUsernameChangeToKeycloak() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername("john_new");

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("john_new")).thenReturn(false);
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(UserResponse.builder().username("john_new").build());

        userService.updateCurrentUser(request);

        assertThat(user.getUsername()).isEqualTo("john_new");

        // Сначала база (ограничения всплывают сразу), затем Keycloak
        InOrder order = inOrder(userRepository, keycloakAdminClient);
        order.verify(userRepository).saveAndFlush(user);
        order.verify(keycloakAdminClient).updateUser(user.getId(), "john_new", "john_doe@example.com");
    }

    @Test
    @DisplayName("Занятый логин отклоняется до записи в базу и обращения к Keycloak")
    void rejectsTakenUsername() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername("taken");

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateCurrentUser(request))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(keycloakAdminClient);
    }

    @Test
    @DisplayName("Пароль меняется только в Keycloak: сервис его не хранит")
    void changesPasswordOnlyInKeycloak() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setPassword("new-password");

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(UserResponse.builder().username("john_doe").build());

        userService.updateCurrentUser(request);

        verify(keycloakAdminClient).updatePassword(user.getId(), "new-password");
        // Логин и e-mail не менялись — лишнего обращения к Keycloak нет
        verify(keycloakAdminClient, never()).updateUser(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Сбой смены пароля откатывает уже применённые логин и e-mail в Keycloak")
    void restoresPreviousCredentialsWhenPasswordChangeFails() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername("john_new");
        request.setPassword("new-password");

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("john_new")).thenReturn(false);
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        doThrow(new KeycloakUnavailableException("Keycloak недоступен"))
                .when(keycloakAdminClient).updatePassword(user.getId(), "new-password");

        assertThatThrownBy(() -> userService.updateCurrentUser(request))
                .isInstanceOf(KeycloakUnavailableException.class);

        InOrder order = inOrder(keycloakAdminClient);
        order.verify(keycloakAdminClient).updateUser(user.getId(), "john_new", "john_doe@example.com");
        order.verify(keycloakAdminClient).updatePassword(user.getId(), "new-password");
        order.verify(keycloakAdminClient).updateUser(user.getId(), "john_doe", "john_doe@example.com");
    }

    @Test
    @DisplayName("Удаление профиля: сначала база, затем учётная запись Keycloak")
    void deletesProfileBeforeKeycloakAccount() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.deleteCurrentUser();

        InOrder order = inOrder(userRepository, keycloakAdminClient);
        order.verify(userRepository).delete(user);
        order.verify(userRepository).flush();
        order.verify(keycloakAdminClient).deleteUser(user.getId());
    }

    @Test
    @DisplayName("Сбой Keycloak при удалении пробрасывается, чтобы транзакция базы откатилась")
    void propagatesKeycloakFailureOnDeletion() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        doThrow(new KeycloakUnavailableException("Keycloak недоступен"))
                .when(keycloakAdminClient).deleteUser(user.getId());

        assertThatThrownBy(() -> userService.deleteCurrentUser())
                .isInstanceOf(KeycloakUnavailableException.class);
    }

    @Test
    @DisplayName("WB-токен расшифровывается и очищается от кавычек")
    void decryptsWbToken() {
        user.setWbToken("encrypted");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(encryptionUtil.decrypt("encrypted")).thenReturn("\"wb-token\"");

        assertThat(userService.getDecryptedWbToken()).isEqualTo("wb-token");
    }

    @Test
    @DisplayName("Отсутствующий WB-токен возвращается как null без расшифровки")
    void returnsNullWhenWbTokenIsNotSet() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThat(userService.getDecryptedWbToken()).isNull();

        verifyNoInteractions(encryptionUtil);
    }
}
