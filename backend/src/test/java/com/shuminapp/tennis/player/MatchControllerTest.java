package com.shuminapp.tennis.player;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MatchController.class)
class MatchControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MatchService service;
    @Test void bindsYearAndPaginationAndReturnsAvailability() throws Exception {
        when(service.history("ATP",1,2024,0,20)).thenReturn(Optional.of(new MatchRepository.History(List.of(),0,0,20,false)));
        mvc.perform(get("/v1/players/ATP/1/matches").param("year","2024"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.items").isArray());
        verify(service).history("ATP",1,2024,0,20);
    }
    @Test void rejectsNonNumericYearBeforeService() throws Exception {
        mvc.perform(get("/v1/players/ATP/1/matches").param("year","bad"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MATCH_QUERY"));
        verifyNoInteractions(service);
    }
    @Test void missingPlayerReturns404() throws Exception {
        when(service.history("ATP",999,null,0,20)).thenReturn(Optional.empty());
        mvc.perform(get("/v1/players/ATP/999/matches")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }
    @Test void unavailableDoesNotExposeDatabaseDetails() throws Exception {
        when(service.history("ATP",1,null,0,20)).thenThrow(new DataAccessResourceFailureException("private database detail"));
        mvc.perform(get("/v1/players/ATP/1/matches")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("MATCHES_UNAVAILABLE")).andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.message").value("Match history is temporarily unavailable."));
    }
}
