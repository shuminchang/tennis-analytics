package com.shuminapp.tennis.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlayerServiceTest {
    private final PlayerRepository repository = mock(PlayerRepository.class);
    private final PlayerService service = new PlayerService(repository);

    @Test void normalizesQueryAndTourBeforeQuerying() {
        var page = new PlayerRepository.SearchPage(List.of(), 0, 0, 20);
        when(repository.search("Roger", "ATP", 0, 20)).thenReturn(page);
        assertThat(service.search(" Roger ", "atp", 0, 20)).isEqualTo(page);
        verify(repository).search("Roger", "ATP", 0, 20);
    }
    @Test void supportsEmptyBrowseWithoutTour() {
        service.search(null, " ", 0, 20);
        verify(repository).search("", null, 0, 20);
    }
    @ParameterizedTest
    @ValueSource(strings={"bad\nname", "bad\tname"})
    void rejectsControlCharacters(String q) {
        assertThrows(IllegalArgumentException.class, () -> service.search(q, null, 0, 20));
        verifyNoInteractions(repository);
    }
    @Test void rejectsUnboundedInputs() {
        assertThrows(IllegalArgumentException.class, () -> service.search("x".repeat(101), null, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.search("", "ITF", 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.search("", null, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.search("", null, 0, 101));
        verifyNoInteractions(repository);
    }
    @ParameterizedTest
    @ValueSource(strings={"ATP", "WTA"})
    void normalizesTourForPlayerDetails(String tour) {
        var player = new PlayerRepository.PlayerSummary(
                tour, 1, "Test", "Player", null, null, null);
        when(repository.findById(tour, 1)).thenReturn(Optional.of(player));

        assertThat(service.findById(" " + tour.toLowerCase(java.util.Locale.ROOT) + " ", 1))
                .contains(player);
        verify(repository).findById(tour, 1);
    }
    @Test void returnsEmptyWhenPlayerIsNotFound() {
        when(repository.findById("ATP", 999)).thenReturn(Optional.empty());

        assertThat(service.findById("ATP", 999)).isEmpty();
        verify(repository).findById("ATP", 999);
    }
    @Test void rejectsMissingTourForPlayerDetails() {
        assertThrows(IllegalArgumentException.class, () -> service.findById(null, 1));
        verifyNoInteractions(repository);
    }
    @ParameterizedTest
    @ValueSource(strings={"", " ", "ITF", "ATP/WTA"})
    void rejectsInvalidTourForPlayerDetails(String tour) {
        assertThrows(IllegalArgumentException.class, () -> service.findById(tour, 1));
        verifyNoInteractions(repository);
    }
    @ParameterizedTest
    @ValueSource(ints={0, -1, Integer.MIN_VALUE})
    void rejectsNonPositivePlayerId(int playerId) {
        assertThrows(IllegalArgumentException.class, () -> service.findById("ATP", playerId));
        verifyNoInteractions(repository);
    }
}
