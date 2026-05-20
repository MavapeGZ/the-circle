package com.thecircle.gamification.controller;

import com.thecircle.gamification.dto.AwardEventResponseDto;
import com.thecircle.gamification.service.GamificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GamificationController.class)
@TestPropertySource(properties = "gamification.internal.api-key=secret")
class GamificationControllerSecurityTest {

    private static final String VALID_BODY = "{\"userId\":1,\"eventType\":\"ITEM_DONATED\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GamificationService service;

    @Test
    void awardEvent_noKey_returns403() throws Exception {
        mockMvc.perform(post("/api/gamification/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void awardEvent_wrongKey_returns403() throws Exception {
        mockMvc.perform(post("/api/gamification/events")
                        .header("X-Internal-Api-Key", "nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void awardEvent_correctKey_returns200() throws Exception {
        when(service.processEvent(any())).thenReturn(new AwardEventResponseDto(1L, 50, 50, List.of()));

        mockMvc.perform(post("/api/gamification/events")
                        .header("X-Internal-Api-Key", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());
    }
}
