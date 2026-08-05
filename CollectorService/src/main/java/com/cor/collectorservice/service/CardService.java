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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CardService {

    CardRepository cardRepository;
    CardMapper cardMapper;
    UserService userService;
    WbApiClient wbApiClient;

    /**
     * Возвращает карточки текущего пользователя.
     * Логика получения: всегда сначала из БД, и только если там пусто —
     * подтягиваем из WB API (через общий лимит запросов), сохраняем в БД и отдаём.
     */
    @Transactional
    public List<CardResponse> getCurrentUserCards() {
        log.info("Получение карточек текущего пользователя");
        User user = getAuthenticatedUser();

        List<Card> cards = cardRepository.findByUser(user);
        if (cards.isEmpty()) {
            log.info("Карточки пользователя {} отсутствуют в БД, загрузка из WB API", user.getUsername());
            cards = fetchAndStoreCardsFromApi(user);
        }

        log.info("Найдено {} карточек для пользователя: {}", cards.size(), user.getUsername());
        return cards.stream()
                .map(cardMapper::toResponse)
                .toList();
    }

    /**
     * Загружает карточки из WB API (под единым лимитом 100 запросов/минуту и
     * общей очередью для всех пользователей), сохраняет их в БД и возвращает.
     */
    private List<Card> fetchAndStoreCardsFromApi(User user) {
        String token = userService.getDecryptedWbToken();
        if (token == null || token.isBlank()) {
            log.warn("WB-токен не установлен для пользователя: {}", user.getUsername());
            throw new BadRequestException("WB-токен не установлен. Укажите токен в профиле");
        }

        List<CardRequest> apiCards = wbApiClient.fetchAllCards(token);
        if (apiCards.isEmpty()) {
            log.info("WB API не вернул карточек для пользователя: {}", user.getUsername());
            return List.of();
        }

        List<Card> cards = apiCards.stream()
                .map(cardMapper::toEntity)
                .peek(card -> card.setUser(user))
                .toList();

        List<Card> saved = cardRepository.saveAll(cards);
        log.info("Сохранено {} карточек из WB API для пользователя: {}", saved.size(), user.getUsername());
        return saved;
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

    /**
     * Возвращает профиль пользователя, от имени которого выполняется запрос.
     * <p>
     * Проверка аутентификации выполнена раньше — в
     * {@link com.cor.collectorservice.filter.JwtAuthenticationFilter}, поэтому здесь
     * достаточно взять профиль по контексту запроса.
     *
     * @return профиль текущего пользователя
     * @throws UnauthorizedAccessException если запрос не аутентифицирован
     */
    private User getAuthenticatedUser() {
        return userService.getAuthenticatedUser();
    }
}
