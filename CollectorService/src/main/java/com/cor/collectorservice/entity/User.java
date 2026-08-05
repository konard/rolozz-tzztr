package com.cor.collectorservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

@Entity(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    
    String username;
    
    String password;

    @Column(name = "wb_token")
    String wbToken;
    
    String role = "USER";

    String email;

    @Column(name = "is_verified", nullable = false)
    Boolean isVerified = false;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Card> cards = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "create_at", updatable = false)
    LocalDateTime createAt;

    @UpdateTimestamp
    @Column(name = "update_at")
    LocalDateTime updateAt;
}
