package com.cor.collectorservice.service;

import com.cor.collectorservice.client.WbApiClient;
import com.cor.collectorservice.dto.card.CardRequest;
import com.cor.collectorservice.dto.card.CardResponse;
import com.cor.collectorservice.entity.Card;
import com.cor.collectorservice.entity.User;
import com.cor.collectorservice.mapper.CardMapper;
import com.cor.collectorservice.repository.CardRepository;
import com.cor.collectorservice.util.context.UserContext;
import com.cor.collectorservice.util.context.UserContextHolder;
import com.cor.collectorservice.util.exception.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты сервиса карточек.
 * <p>
 * Контекст пользователя после отказа от Spring Security задаётся через
 * {@link UserContextHolder} — так же, как это делает
 * {@link com.cor.collectorservice.filter.JwtAuthenticationFilter} после проверки JWT.
 */
@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;
    @Mock
    private CardMapper cardMapper;
    @Mock
    private UserService userService;
    @Mock
    private WbApiClient wbApiClient;

    @InjectMocks
    private CardService cardService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("tester");

        UserContextHolder.set(new UserContext(user.getId(), "tester", "tester@example.com", Set.of("USER")));
        when(userService.getAuthenticatedUser()).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void returnsCardsFromDbWithoutCallingApi() {
        Card card = new Card();
        when(cardRepository.findByUser(user)).thenReturn(List.of(card));
        when(cardMapper.toResponse(card)).thenReturn(new CardResponse());

        List<CardResponse> result = cardService.getCurrentUserCards();

        assertThat(result).hasSize(1);
        verify(wbApiClient, never()).fetchAllCards(any());
        verify(cardRepository, never()).saveAll(any());
    }

    @Test
    void fetchesFromApiAndStoresWhenDbEmpty() {
        when(cardRepository.findByUser(user)).thenReturn(List.of());
        when(userService.getDecryptedWbToken()).thenReturn("wb-token");

        CardRequest request = CardRequest.builder().nmID(1L).build();
        when(wbApiClient.fetchAllCards("wb-token")).thenReturn(List.of(request));

        Card mapped = new Card();
        when(cardMapper.toEntity(request)).thenReturn(mapped);
        when(cardRepository.saveAll(any())).thenReturn(List.of(mapped));
        when(cardMapper.toResponse(mapped)).thenReturn(new CardResponse());

        List<CardResponse> result = cardService.getCurrentUserCards();

        assertThat(result).hasSize(1);
        verify(wbApiClient).fetchAllCards("wb-token");

        // Карточки, полученные из API, привязываются к пользователю и сохраняются в БД
        ArgumentCaptor<List<Card>> captor = ArgumentCaptor.forClass(List.class);
        verify(cardRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(mapped);
        assertThat(mapped.getUser()).isEqualTo(user);
    }

    @Test
    void throwsWhenWbTokenMissing() {
        when(cardRepository.findByUser(user)).thenReturn(List.of());
        when(userService.getDecryptedWbToken()).thenReturn(null);

        assertThatThrownBy(() -> cardService.getCurrentUserCards())
                .isInstanceOf(BadRequestException.class);

        verify(wbApiClient, never()).fetchAllCards(any());
    }
}
