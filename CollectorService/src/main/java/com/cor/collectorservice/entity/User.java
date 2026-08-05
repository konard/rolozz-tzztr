package com.cor.collectorservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Профиль пользователя в базе данных сервиса.
 * <p>
 * После перехода на Keycloak сущность не хранит пароль: учётными данными
 * управляет Keycloak. Идентификатор не генерируется базой — он присваивается
 * из идентификатора учётной записи Keycloak на первом шаге регистрации
 * (см. {@link com.cor.collectorservice.service.UserProfileRegistrar}),
 * благодаря чему профиль однозначно связан с учётной записью, а claim {@code sub}
 * access-токена можно использовать как первичный ключ при поиске профиля.
 */
@Entity(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {

    /**
     * Идентификатор учётной записи Keycloak, он же первичный ключ профиля.
     */
    @Id
    UUID id;

    /**
     * Логин пользователя; дублирует логин учётной записи Keycloak.
     */
    String username;

    /**
     * Токен Wildberries в зашифрованном виде.
     */
    @Column(name = "wb_token")
    String wbToken;

    /**
     * Роль пользователя. Источник истины при авторизации — realm-роли в access-токене Keycloak,
     * это поле хранится для отображения в профиле и отчётах.
     */
    String role = "USER";

    /**
     * E-mail пользователя; дублирует e-mail учётной записи Keycloak.
     */
    String email;

    /**
     * Признак подтверждения e-mail на стороне сервиса.
     */
    @Column(name = "is_verified", nullable = false)
    Boolean isVerified = false;

    /**
     * Карточки товаров пользователя; удаляются вместе с профилем.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Card> cards = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "create_at", updatable = false)
    LocalDateTime createAt;

    @UpdateTimestamp
    @Column(name = "update_at")
    LocalDateTime updateAt;
}
