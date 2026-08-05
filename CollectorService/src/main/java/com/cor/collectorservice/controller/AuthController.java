package com.cor.collectorservice.controller;

import com.cor.collectorservice.dto.auth.AuthResponse;
import com.cor.collectorservice.dto.auth.ErrorResponse;
import com.cor.collectorservice.dto.auth.LoginRequest;
import com.cor.collectorservice.dto.auth.RefreshTokenRequest;
import com.cor.collectorservice.dto.auth.RegisterRequest;
import com.cor.collectorservice.dto.auth.TokenResponse;
import com.cor.collectorservice.dto.auth.UserResponse;
import com.cor.collectorservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * Публичные эндпоинты аутентификации: регистрация, вход, продление и завершение сессии.
 * <p>
 * Все методы контроллера доступны без access-токена (см. {@code keycloak.jwt.public-paths}),
 * поэтому аннотация {@link com.cor.collectorservice.util.annotation.RequiresRole} здесь не применяется.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirements
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Аутентификация", description = "Регистрация и вход через Keycloak")
public class AuthController {

    AuthService authService;

    /**
     * Регистрирует нового пользователя.
     *
     * @param request данные регистрации
     * @return профиль созданного пользователя
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Регистрация нового пользователя",
            description = """
                    Создаёт пользователя сначала в Keycloak, затем — профиль в базе данных сервиса.

                    Порядок выполнения:
                    1. Проверяется, что логин не занят в базе данных сервиса.
                    2. В Keycloak создаётся учётная запись и назначается realm-роль `USER`.
                       Идентификатор, выданный Keycloak, становится идентификатором профиля.
                    3. Профиль сохраняется в базе данных сервиса в отдельной транзакции.
                    4. Если сохранение профиля откатилось, учётная запись удаляется из Keycloak
                       компенсирующей операцией. Если Keycloak в этот момент недоступен,
                       удаление повторяется фоновым механизмом ретрая, а клиент получает 500 —
                       регистрацию можно безопасно повторить.

                    Пароль хранится только в Keycloak: сервис его не сохраняет и не хэширует.
                    Необязательный `wbToken` шифруется перед сохранением в базу данных.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Пользователь зарегистрирован",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Данные запроса не прошли валидацию",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Логин или e-mail уже заняты",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Профиль не сохранён, изменения отменены компенсацией",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Keycloak отказал в создании учётной записи",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Keycloak недоступен",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/auth/register: регистрация пользователя {}", request.getUsername());
        UserResponse response = authService.register(request);
        log.info("POST /api/auth/register: пользователь {} зарегистрирован с идентификатором {}",
                response.getUsername(), response.getId());
        return response;
    }

    /**
     * Выполняет вход в систему.
     *
     * @param request логин и пароль
     * @return токены Keycloak и профиль пользователя
     */
    @PostMapping("/login")
    @Operation(
            summary = "Вход в систему",
            description = """
                    Проверяет учётные данные в Keycloak по схеме Direct Access Grant
                    (grant type `password`) и возвращает пару токенов вместе с профилем пользователя.

                    Полученный `access_token` нужно передавать во всех последующих запросах
                    в заголовке `Authorization: Bearer <access_token>`.
                    Когда срок его действия истечёт, пару токенов можно продлить
                    через `POST /api/auth/refresh`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Вход выполнен",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Данные запроса не прошли валидацию",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Неверный логин или пароль",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Учётная запись есть в Keycloak, но профиль отсутствует",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Keycloak недоступен",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/auth/login: вход пользователя {}", request.getUsername());
        AuthResponse response = authService.login(request);
        log.info("POST /api/auth/login: пользователь {} вошёл в систему", request.getUsername());
        return response;
    }

    /**
     * Продлевает сессию по refresh-токену.
     *
     * @param request refresh-токен
     * @return новая пара токенов
     */
    @PostMapping("/refresh")
    @Operation(
            summary = "Обновление пары токенов",
            description = """
                    Обменивает действующий refresh-токен на новую пару токенов Keycloak.
                    Используется, когда срок жизни access-токена истёк,
                    чтобы не запрашивать у пользователя пароль повторно.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Токены обновлены",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Refresh-токен не передан",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh-токен просрочен или отозван",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Keycloak недоступен",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("POST /api/auth/refresh: обновление пары токенов");
        return authService.refresh(request.refreshToken());
    }

    /**
     * Завершает сессию пользователя в Keycloak.
     *
     * @param request refresh-токен, полученный при входе
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Выход из системы",
            description = """
                    Отзывает refresh-токен в Keycloak и завершает сессию пользователя.
                    Ранее выданный access-токен продолжит действовать до истечения своего срока жизни:
                    он проверяется по подписи и не требует обращения к Keycloak.
                    Уже отозванный или просроченный токен ошибкой не считается.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Сессия завершена"),
            @ApiResponse(responseCode = "400", description = "Refresh-токен не передан",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Keycloak недоступен",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("POST /api/auth/logout: завершение сессии");
        authService.logout(request.refreshToken());
    }
}
