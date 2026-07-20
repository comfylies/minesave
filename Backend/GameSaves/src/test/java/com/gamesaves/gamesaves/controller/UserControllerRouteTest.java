package com.gamesaves.gamesaves.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

class UserControllerRouteTest {

    @Test
    void mustNotExposeAnIdBasedProfileUpdateRoute() {
        boolean exposesPutRoute = Arrays.stream(UserController.class.getDeclaredMethods())
                .map(Method::getAnnotations)
                .flatMap(Arrays::stream)
                .anyMatch(annotation -> annotation.annotationType().equals(PutMapping.class));

        assertFalse(exposesPutRoute,
                "Profile writes must use the authenticated account, never /api/users/{id}");
    }
}
