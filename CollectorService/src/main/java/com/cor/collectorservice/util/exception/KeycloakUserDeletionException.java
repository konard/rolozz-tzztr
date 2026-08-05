package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Не удалось удалить пользователя из Keycloak.
 * <p>
 * Возникает при компенсации регистрации (откат записи в Keycloak после неудачного сохранения в БД)
 * и при удалении профиля. При компенсации исключение перехватывается
 * {@link com.cor.collectorservice.service.RegistrationCompensationService},
 * который ставит удаление в очередь фонового ретрая.
 */
public class KeycloakUserDeletionException extends KeycloakException {

    /**
     * @param message описание причины отказа, включая идентификатор пользователя
     */
    public KeycloakUserDeletionException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    /**
     * @param message описание причины отказа
     * @param cause   исходная ошибка Admin API
     */
    public KeycloakUserDeletionException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY, cause);
    }
}
