package com.codeinsight.api.web;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    void analyzeReturnsCreatedWithDetectedStack() throws Exception {
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repoUrl\":\"https://github.com/spring-projects/spring-petclinic\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectName", is("spring-petclinic")))
                .andExpect(jsonPath("$.framework", is("Spring Boot")))
                .andExpect(jsonPath("$.components[0]", is("Controllers")));
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
}
