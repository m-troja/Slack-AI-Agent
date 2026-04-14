package com.michal.openai.controllers;

import com.michal.openai.gpt.service.SlackGptCoordinator;
import com.michal.openai.slack.SlackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SlackController.class)
class SlackRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SlackGptCoordinator slackGptCoordinator;

    private String slackRequest;

    @BeforeEach
    void setup() throws IOException {
        slackRequest = Files.readString(Paths.get("src/test/resources/SlackRequestSayHi.json"));
    }

    @Test
    void shouldReturnOkAndCallSlackService() throws Exception {
        mockMvc.perform(post("/api/v1/slack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slackRequest))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(slackGptCoordinator, times(1))
                .processMention(slackRequest);
    }
}
