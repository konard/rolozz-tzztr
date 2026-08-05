package com.cor.collectorservice.controller;

import com.cor.collectorservice.configs.AppConfig;
import com.cor.collectorservice.dto.auth.ErrorResponse;
import com.cor.collectorservice.dto.card.CardResponse;
import com.cor.collectorservice.dto.card.UpdateCustomArticleRequest;
import com.cor.collectorservice.service.CardService;
import com.cor.collectorservice.util.annotation.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Операции с карточками товаров текущего пользователя.
 * <p>
 * Все методы требуют заголовок {@code Authorization: Bearer <access_token>} с токеном Keycloak
 * и realm-роль {@code USER} (см. {@link RequiresRole}).
 */
@Slf4j
@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@RequiresRole("USER")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Карточки", description = "Операции с карточками товаров Wildberries")
@SecurityRequirement(name = AppConfig.SECURITY_SCHEME_NAME)
public class CardController {

    CardService cardService;

    /**
     * Возвращает карточки текущего пользователя.
     *
     * @return список карточек
     */
    @GetMapping("current")
    @Operation(
            summary = "Карточки текущего пользователя",
            description = """
                    Возвращает карточки товаров пользователя, которому принадлежит access-токен.

                    Источник данных выбирается так: сначала карточки читаются из базы данных сервиса,
                    и только если там пусто — загружаются из WB API по сохранённому в профиле WB-токену,
                    сохраняются в базу и возвращаются. Запросы к WB API выполняются последовательно
                    под общим для всех пользователей ограничением частоты.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Карточки получены",
                    content = @Content(schema = @Schema(implementation = CardResponse.class))),
            @ApiResponse(responseCode = "400", description = "WB-токен не указан в профиле пользователя",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует, просрочен или недействителен",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "У пользователя нет роли USER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Превышен лимит запросов к WB API",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "WB API вернул ошибку",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<CardResponse> getCurrentUserCards() {
        log.info("GET /api/cards/current: запрос карточек текущего пользователя");
        List<CardResponse> cards = cardService.getCurrentUserCards();
        log.info("GET /api/cards/current: отправлено {} карточек", cards.size());
        return cards;
    }

    /**
     * Возвращает карточку по её идентификатору в WB.
     *
     * @param nmId идентификатор карточки в WB
     * @return карточка товара
     */
    @GetMapping("/get/{nmId}")
    @Operation(
            summary = "Карточка по идентификатору",
            description = """
                    Возвращает карточку по её идентификатору `nmID` в Wildberries.

                    Доступ ограничен владельцем: если карточка принадлежит другому пользователю,
                    запрос отклоняется с кодом 401, а сама попытка фиксируется в логах.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Карточка получена",
                    content = @Content(schema = @Schema(implementation = CardResponse.class))),
            @ApiResponse(responseCode = "400", description = "Карточка не найдена",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Токен недействителен либо карточка чужая",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "У пользователя нет роли USER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CardResponse getCardById(@PathVariable Long nmId) {
        log.info("GET /api/cards/get/{}: запрос карточки", nmId);
        CardResponse card = cardService.getCardById(nmId);
        log.info("GET /api/cards/get/{}: карточка отправлена", nmId);
        return card;
    }

    /**
     * Обновляет пользовательский артикул карточки.
     *
     * @param nmId    идентификатор карточки в WB
     * @param request новый пользовательский артикул
     * @return обновлённая карточка
     */
    @PutMapping("/get/{nmId}/custom-article")
    @Operation(
            summary = "Обновление пользовательского артикула",
            description = """
                    Задаёт собственный артикул карточки — он хранится только в базе данных сервиса
                    и в Wildberries не передаётся.

                    Изменять артикул может только владелец карточки.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Артикул обновлён",
                    content = @Content(schema = @Schema(implementation = CardResponse.class))),
            @ApiResponse(responseCode = "400", description = "Карточка не найдена либо данные некорректны",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Токен недействителен либо карточка чужая",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "У пользователя нет роли USER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CardResponse updateCustomArticle(
            @PathVariable Long nmId,
            @Valid @RequestBody UpdateCustomArticleRequest request
    ) {
        log.info("PUT /api/cards/get/{}/custom-article: обновление пользовательского артикула", nmId);
        CardResponse card = cardService.updateCustomArticle(nmId, request);
        log.info("PUT /api/cards/get/{}/custom-article: артикул обновлён", nmId);
        return card;
    }
}
