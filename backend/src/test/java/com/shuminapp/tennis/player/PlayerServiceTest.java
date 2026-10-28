package com.shuminapp.tennis.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import java.util.List;
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
}
