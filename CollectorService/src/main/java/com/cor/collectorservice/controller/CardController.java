package com.cor.collectorservice.controller;

import com.cor.collectorservice.dto.card.CardResponse;
import com.cor.collectorservice.dto.card.UpdateCustomArticleRequest;
import com.cor.collectorservice.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Cards", description = "Операции с карточками товаров")
@SecurityRequirement(name = "sessionAuth")
public class CardController {

    CardService cardService;


    @GetMapping("/sync")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Получить все карточки текущего пользователя (с синхронизацией с WB API)")
    public List<CardResponse> syncCards() {
        log.info("GET запрос на получение всех карточек с синхронизацией");
        List<CardResponse> cards = cardService.syncAndGetCards();
        log.info("Отправлено {} карточек", cards.size());
        return cards;
    }

    @GetMapping("current")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Получить все карточки текущего пользователя")
    public List<CardResponse> getCurrentUserCards() {
        log.info("GET запрос на получение всех карточек текущего пользователя");
        List<CardResponse> cards = cardService.getCurrentUserCards();
        log.info("Отправлено {} карточек", cards.size());
        return cards;
    }

    @GetMapping("/get/{nmId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Получить карточку по ID")
    public CardResponse getCardById(@PathVariable Long nmId) {
        log.info("GET запрос на получение карточки по ID: {}", nmId);
        CardResponse card = cardService.getCardById(nmId);
        log.info("Карточка успешно отправлена: {}", nmId);
        return card;
    }

    @PutMapping("/get/{nmId}/custom-article")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Обновить кастомный артикул карточки")
    public CardResponse updateCustomArticle(
            @PathVariable Long nmId,
            @Valid @RequestBody UpdateCustomArticleRequest request
    ) {
        log.info("PUT запрос на обновление кастомного артикула для карточки: {}", nmId);
        CardResponse card = cardService.updateCustomArticle(nmId, request);
        log.info("Кастомный артикул успешно обновлен для карточки: {}", nmId);
        return card;
    }
}
