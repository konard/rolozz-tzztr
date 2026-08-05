package com.cor.collectorservice.controller;

import com.cor.collectorservice.configs.AppConfig;
import com.cor.collectorservice.dto.auth.ErrorResponse;
import com.cor.collectorservice.dto.auth.UpdateUserRequest;
import com.cor.collectorservice.dto.auth.UserResponse;
import com.cor.collectorservice.service.UserService;
import com.cor.collectorservice.util.annotation.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Операции с профилем текущего пользователя.
 * <p>
 * Все методы требуют заголовок {@code Authorization: Bearer <access_token>} с токеном Keycloak
 * и realm-роль {@code USER}: подпись токена проверяет
 * {@link com.cor.collectorservice.filter.JwtAuthenticationFilter}, роль —
 * {@link com.cor.collectorservice.util.interceptor.RoleAuthorizationInterceptor}
 * по аннотации {@link RequiresRole}.
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@RequiresRole("USER")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Пользователи", description = "Операции с профилем текущего пользователя")
@SecurityRequirement(name = AppConfig.SECURITY_SCHEME_NAME)
public class UserController {

    UserService userService;

    /**
     * Возвращает профиль пользователя, которому принадлежит access-токен.
     *
     * @return профиль текущего пользователя
     */
    @GetMapping("/me")
    @Operation(
            summary = "Профиль текущего пользователя",
            description = """
                    Возвращает профиль пользователя, которому принадлежит переданный access-токен.

                    Пользователь определяется по claim `sub` токена: он совпадает с идентификатором
                    профиля в базе данных сервиса, поэтому смена логина не влияет на поиск профиля.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Профиль получен",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует, просрочен или недействителен",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "У пользователя нет роли USER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Профиль пользователя не найден в базе данных",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public UserResponse getCurrentUser() {
        log.info("GET /api/users/me: запрос профиля текущего пользователя");
        UserResponse response = userService.getCurrentUser();
        log.info("GET /api/users/me: профиль пользователя {} отправлен", response.getUsername());
        return response;
    }

    /**
     * Обновляет профиль текущего пользователя.
     *
     * @param request поля, которые нужно изменить
     * @return обновлённый профиль
     */
    @PutMapping("/me")
    @Operation(
            summary = "Обновление профиля текущего пользователя",
            description = """
                    Изменяет логин, e-mail, пароль или WB-токен текущего пользователя.
                    Достаточно передать любое одно поле — остальные останутся без изменений.

                    Где что хранится:
                    * логин и e-mail дублируются в Keycloak и в базе данных сервиса
                      и обновляются в обоих хранилищах;
                    * пароль хранится только в Keycloak — сервис его не сохраняет;
                    * WB-токен хранится только в базе данных сервиса в зашифрованном виде.

                    Если Keycloak отклонит изменение, транзакция базы данных откатывается,
                    а уже применённые изменения логина и e-mail в Keycloak отменяются:
                    профиль остаётся в прежнем состоянии целиком.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Профиль обновлён",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Не указано ни одного поля либо данные некорректны",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует, просрочен или недействителен",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "У пользователя нет роли USER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Новый логин или e-mail уже заняты",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Keycloak отказал в обновлении учётной записи",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Keycloak недоступен",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public UserResponse updateCurrentUser(@Valid @RequestBody UpdateUserRequest request) {
        log.info("PUT /api/users/me: обновление профиля текущего пользователя");
        UserResponse response = userService.updateCurrentUser(request);
        log.info("PUT /api/users/me: профиль пользователя {} обновлён", response.getUsername());
        return response;
    }

    /**
     * Удаляет профиль текущего пользователя и его учётную запись в Keycloak.
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Удаление текущего пользователя",
            description = """
                    Удаляет профиль пользователя из базы данных сервиса вместе с его карточками,
                    после чего удаляет учётную запись из Keycloak.

                    Если Keycloak в этот момент недоступен, удаление профиля откатывается,
                    и операцию можно безопасно повторить: учётная запись без профиля не остаётся.
                    Ранее выданный access-токен продолжит проходить проверку подписи до истечения
                    своего срока жизни, но запросы к профилю будут отклоняться с кодом 404.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Пользователь удалён"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует, просрочен или недействителен",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "У пользователя нет роли USER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Профиль пользователя не найден в базе данных",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Keycloak недоступен, удаление отменено",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void deleteCurrentUser() {
        log.info("DELETE /api/users/me: удаление текущего пользователя");
        userService.deleteCurrentUser();
        log.info("DELETE /api/users/me: текущий пользователь удалён");
    }
}
