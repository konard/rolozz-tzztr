package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Регистрация не завершена: пользователь был создан в Keycloak,
 * но сохранение профиля в базе данных сервиса откатилось.
 * <p>
 * К моменту выброса исключения уже запущена компенсация — удаление пользователя из Keycloak
 * (немедленное или через фоновый механизм ретрая), поэтому клиент может безопасно повторить регистрацию.
 */
public class RegistrationFailedException extends BaseException {

    /**
     * @param username логин пользователя, регистрацию которого не удалось завершить
     * @param cause    исходная ошибка сохранения в БД
     */
    public RegistrationFailedException(String username, Throwable cause) {
        super("Не удалось завершить регистрацию пользователя: " + username
                + ". Изменения отменены, попробуйте позже", HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
