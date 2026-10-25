package ru.practicum.ewm.stats.server.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StatsControllerTest {

    private static final String HIT_BODY = "{\"app\":\"ewm-main-service\",\"uri\":\"/events/1\","
            + "\"ip\":\"10.0.0.1\",\"timestamp\":\"2024-01-01 10:00:00\"}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldSaveHitAndReturnStatsWithExactFields() throws Exception {
        mockMvc.perform(post("/hit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HIT_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/stats")
                        .param("start", "2024-01-01 00:00:00")
                        .param("end", "2024-01-02 00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].app").value("ewm-main-service"))
                .andExpect(jsonPath("$[0].uri").value("/events/1"))
                .andExpect(jsonPath("$[0].hits").value(1))
                .andExpect(jsonPath("$[0].id").doesNotExist());
    }

    @Test
    void shouldBindUrisAndUniqueFlag() throws Exception {
        saveHit("/events/1", "10.0.0.1");
        saveHit("/events/1", "10.0.0.2");
        saveHit("/events/2", "10.0.0.3");

        mockMvc.perform(get("/stats")
                        .param("start", "2024-01-01 00:00:00")
                        .param("end", "2024-01-02 00:00:00")
                        .param("uris", "/events/1")
                        .param("unique", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].uri").value("/events/1"))
                .andExpect(jsonPath("$[0].hits").value(2));
    }

    @Test
    void shouldRejectStartAfterEnd() throws Exception {
        mockMvc.perform(get("/stats")
                        .param("start", "2024-01-02 00:00:00")
                        .param("end", "2024-01-01 00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void shouldRejectMissingRequiredParameter() throws Exception {
        mockMvc.perform(get("/stats")
                        .param("end", "2024-01-02 00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"));
    }

    @Test
    void shouldRejectUnparseableDate() throws Exception {
        mockMvc.perform(get("/stats")
                        .param("start", "not-a-date")
                        .param("end", "2024-01-02 00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"));
    }

    @Test
    void shouldRejectInvalidHitBody() throws Exception {
        mockMvc.perform(post("/hit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"));
    }

    private void saveHit(String uri, String ip) throws Exception {
        String body = "{\"app\":\"ewm-main-service\",\"uri\":\"" + uri + "\",\"ip\":\"" + ip + "\","
                + "\"timestamp\":\"2024-01-01 10:00:00\"}";
        mockMvc.perform(post("/hit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
