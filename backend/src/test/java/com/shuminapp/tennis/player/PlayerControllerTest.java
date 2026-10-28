package com.shuminapp.tennis.player;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlayerController.class)
class PlayerControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PlayerService service;
    @Test void returnsPageContract() throws Exception {
        when(service.search("Roger", "ATP", 0, 20)).thenReturn(new PlayerRepository.SearchPage(List.of(),0,0,20));
        mvc.perform(get("/v1/players").param("q","Roger").param("tour","ATP"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.size").value(20));
    }
    @Test void rejectsNonNumericPageBeforeService() throws Exception {
        mvc.perform(get("/v1/players").param("page","bad"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SEARCH"));
        verifyNoInteractions(service);
    }
    @Test void hidesDatabaseDetailsOnUnavailable() throws Exception {
        when(service.search(anyString(),isNull(),anyInt(),anyInt())).thenThrow(new DataAccessResourceFailureException("private database detail"));
        mvc.perform(get("/v1/players")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("SEARCH_UNAVAILABLE"))
            .andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.message").value("Player search is temporarily unavailable."));
    }
}
