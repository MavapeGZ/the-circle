package com.thecircle.gamification.controller;

import com.thecircle.gamification.dto.AwardEventResponseDto;
import com.thecircle.gamification.dto.LeaderboardEntryDto;
import com.thecircle.gamification.dto.UserSummaryDto;
import com.thecircle.gamification.service.GamificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GamificationController.class)
class GamificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GamificationService service;

    @Test
    void awardEvent_validPayload_returns200() throws Exception {
        when(service.processEvent(any())).thenReturn(new AwardEventResponseDto(1L, 50, 50, List.of()));

        mockMvc.perform(post("/api/gamification/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"eventType\":\"ITEM_DONATED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(50));
    }

    @Test
    void awardEvent_missingUserId_returns400() throws Exception {
        mockMvc.perform(post("/api/gamification/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"ITEM_DONATED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void awardEvent_missingEventType_returns400() throws Exception {
        mockMvc.perform(post("/api/gamification/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserSummary_returns200() throws Exception {
        when(service.getUserSummary(1L)).thenReturn(new UserSummaryDto(1L, 80, List.of(), List.of()));

        mockMvc.perform(get("/api/gamification/users/1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(80));
    }

    @Test
    void getLeaderboard_returns200() throws Exception {
        when(service.getLeaderboard()).thenReturn(List.of(new LeaderboardEntryDto(1, 7L, 300)));

        mockMvc.perform(get("/api/gamification/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(7));
    }
}
