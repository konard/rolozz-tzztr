package com.cor.collectorservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    
    String role = "User";

    @CreationTimestamp
    @Column(updatable = false)
    String createAt;

    @UpdateTimestamp
    String updateAt;
}
