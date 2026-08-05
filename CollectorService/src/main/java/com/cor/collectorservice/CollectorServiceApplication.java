package com.cor.collectorservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа сервиса сбора данных Wildberries.
 * <p>
 * {@link EnableScheduling} включает планировщик, который нужен фоновому механизму
 * повторов компенсации регистрации
 * ({@link com.cor.collectorservice.service.RegistrationCompensationService}):
 * без него «осиротевшие» учётные записи Keycloak не удалялись бы повторно.
 */
@EnableScheduling
@SpringBootApplication
public class CollectorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollectorServiceApplication.class, args);
    }

}
