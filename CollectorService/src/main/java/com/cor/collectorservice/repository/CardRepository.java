package com.cor.collectorservice.repository;

import com.cor.collectorservice.entity.Card;
import com.cor.collectorservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByUser(User user);

    List<Card> findByUserId(java.util.UUID userId);

    Optional<Card> findByNmID(Long nmID);
}
