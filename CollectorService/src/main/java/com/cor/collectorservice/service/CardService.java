package com.cor.collectorservice.service;

import com.cor.collectorservice.client.WbApiClient;
import com.cor.collectorservice.dto.card.CardRequest;
import com.cor.collectorservice.dto.card.CardResponse;
import com.cor.collectorservice.dto.card.UpdateCustomArticleRequest;
import com.cor.collectorservice.entity.Card;
import com.cor.collectorservice.entity.User;
import com.cor.collectorservice.mapper.CardMapper;
import com.cor.collectorservice.repository.CardRepository;
import com.cor.collectorservice.util.exception.BadRequestException;
import com.cor.collectorservice.util.exception.UnauthorizedAccessException;
import com.cor.collectorservice.util.exception.WbSyncException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CardService {

    CardRepository cardRepository;
    CardMapper cardMapper;
    UserService userService;
    WbApiClient wbApiClient;

    @Transactional(readOnly = true)
    public List<CardResponse> getCurrentUserCards() {
        log.info("Получение карточек текущего пользователя");
        User user = getAuthenticatedUser();
        List<Card> cards = cardRepository.findByUser(user);
        log.info("Найдено {} карточек для пользователя: {}", cards.size(), user.getUsername());
        return cards.stream()
                .map(cardMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CardResponse getCardById(Long nmId) {
        log.info("Получение карточки по ID: {}", nmId);
        User user = getAuthenticatedUser();
        Card card = cardRepository.findByNmID(nmId)
                .orElseThrow(() -> {
                    log.warn("Карточка с ID {} не найдена", nmId);
                    return new BadRequestException("Карточка не найдена");
                });

        if (!card.getUser().getId().equals(user.getId())) {
            log.warn("Попытка доступа к чужой карточке. Пользователь: {}, Владелец карточки: {}", 
                    user.getUsername(), card.getUser().getUsername());
            throw new UnauthorizedAccessException("Доступ к этой карточке запрещен");
        }

        log.info("Карточка успешно получена: {}", nmId);
        return cardMapper.toResponse(card);
    }

    @Transactional
    public CardResponse updateCustomArticle(Long nmId, UpdateCustomArticleRequest request) {
        log.info("Обновление кастомного артикула для карточки: {}", nmId);
        User user = getAuthenticatedUser();
        Card card = cardRepository.findByNmID(nmId)
                .orElseThrow(() -> {
                    log.warn("Карточка с ID {} не найдена", nmId);
                    return new BadRequestException("Карточка не найдена");
                });

        if (!card.getUser().getId().equals(user.getId())) {
            log.warn("Попытка редактирования чужой карточки. Пользователь: {}, Владелец карточки: {}", 
                    user.getUsername(), card.getUser().getUsername());
            throw new UnauthorizedAccessException("Доступ к этой карточке запрещен");
        }

        log.debug("Установка кастомного артикула: {} для карточки: {}", request.getCustomArticle(), nmId);
        card.setCustomArticle(request.getCustomArticle());
        Card updatedCard = cardRepository.save(card);
        log.info("Кастомный артикул успешно обновлен для карточки: {}", nmId);
        return cardMapper.toResponse(updatedCard);
    }

    @Transactional
    public List<CardResponse> syncAndGetCards() {
        log.info("Начало синхронизации карточек с WB API");

        User user = getAuthenticatedUser();
        String token = userService.getDecryptedWbToken();

        if (token == null || token.isEmpty()) {
            log.warn("WB токен не установлен для пользователя: {}", user.getUsername());
            throw new BadRequestException("Wildberries токен не найден. Пожалуйста, обновите токен в профиле.");
        }

        log.info("Получение карточек из WB API для пользователя: {}", user.getUsername());

        try {
            List<CardRequest> wbCards = wbApiClient.fetchAllCards(token);
            log.info("Получено {} карточек из WB API", wbCards.size());

            List<Card> existingCards = cardRepository.findByUser(user);
            Map<Long, Card> existingCardsMap = existingCards.stream()
                    .collect(Collectors.toMap(Card::getNmID, Function.identity()));
            log.debug("Найдено {} существующих карточек в БД", existingCards.size());

            List<Card> cardsToSave = wbCards.stream()
                    .map(cardRequest -> {
                        Card card = existingCardsMap.get(cardRequest.getNmID());
                        if (card != null) {
                            String customArticle = card.getCustomArticle();
                            card = cardMapper.toEntity(cardRequest);
                            card.setUser(user);
                            card.setCustomArticle(customArticle);
                            log.debug("Обновлена карточка с nmID: {}", card.getNmID());
                        } else {
                            card = cardMapper.toEntity(cardRequest);
                            card.setUser(user);
                            log.debug("Добавлена новая карточка с nmID: {}", card.getNmID());
                        }
                        return card;
                    })
                    .collect(Collectors.toList());

            List<Card> savedCards = cardRepository.saveAll(cardsToSave);
            log.info("Сохранено {} карточек в БД (обновлено + добавлено)", savedCards.size());

            return savedCards.stream()
                    .map(cardMapper::toResponse)
                    .toList();

        } catch (Exception e) {
            log.error("Ошибка синхронизации карточек с WB API: {}", e.getMessage(), e);
            throw new WbSyncException("Не удалось синхронизировать карточки с Wildberries: " + e.getMessage(), e);
        }
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            log.warn("Попытка доступа без аутентификации");
            throw new UnauthorizedAccessException();
        }

        String username = authentication.getName();
        return userService.getAuthenticatedUser();
    }
}
