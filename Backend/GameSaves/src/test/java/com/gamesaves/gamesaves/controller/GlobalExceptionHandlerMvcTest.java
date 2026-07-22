package com.gamesaves.gamesaves.controller;

import com.gamesaves.gamesaves.exception.ForbiddenException;
import com.gamesaves.gamesaves.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerMvcTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ForbiddenTestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void mapsForbiddenExceptionToHttp403() throws Exception {
        mockMvc.perform(get("/test/forbidden").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @RestController
    static class ForbiddenTestController {

        @GetMapping("/test/forbidden")
        void forbidden() {
            throw new ForbiddenException("not a participant");
        }
    }
}
