package com.gamesaves.gamesaves.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MessageControllerRouteTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void conversationsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/messages/conversations"))
                .andExpect(status().isUnauthorized());
    }
}
