package com.gamesaves.gamesaves.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/storage/messages")
public class MessageStaticResourceBlockController {
    @GetMapping("/**")
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void blockDirectMessageStorage() {
    }
}
