package com.codeinsight.api.web;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void analyzeReturnsCreatedWithDetectedStack() throws Exception {
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/spring-projects/spring-petclinic\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectName", is("spring-petclinic")))
                .andExpect(jsonPath("$.framework", is("Spring Boot")))
                .andExpect(jsonPath("$.components[0]", is("Controllers")))
                .andExpect(jsonPath("$.source", is("HEURISTIC")));
    }

    @Test
    void analyzeRejectsInvalidUrl() throws Exception {
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void historyReturnsOk() throws Exception {
        mockMvc.perform(get("/api/analyses"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownIdReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/analyses/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void deleteByIdRemovesTheRecord() throws Exception {
        String body = mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/acme/delete-me\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(delete("/api/analyses/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/analyses/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void deleteByIdReturnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(delete("/api/analyses/999999")).andExpect(status().isNotFound());
    }

    @Test
    void deleteAllRemovesEveryRecord() throws Exception {
        String bodyA = mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/acme/clear-a\"}"))
                .andReturn().getResponse().getContentAsString();
        String bodyB = mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/acme/clear-b\"}"))
                .andReturn().getResponse().getContentAsString();
        long idA = objectMapper.readTree(bodyA).get("id").asLong();
        long idB = objectMapper.readTree(bodyB).get("id").asLong();

        mockMvc.perform(delete("/api/analyses")).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/analyses/" + idA)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/analyses/" + idB)).andExpect(status().isNotFound());
    }
}
