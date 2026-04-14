package com.michal.openai.controllers;

import com.michal.openai.gpt.service.GptService;
import com.michal.openai.gpt.tool.registry.GptToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private GptService gptService;
    @MockitoBean private GptToolRegistry gptToolRegistry;

    @Test
    void shouldReturnOkOnClearDatabase() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/clear-database"))
                .andExpect(status().isOk())
                .andExpect(content().string("Cleared database"));
        verify(gptService, times(1))
                .clearDatabase();
    }
}
