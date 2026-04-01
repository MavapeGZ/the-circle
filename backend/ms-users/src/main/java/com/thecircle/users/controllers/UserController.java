package com.thecircle.users.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @GetMapping("/api/users/health")
    public String health() {
        return "ms-users OK";
    }
}

