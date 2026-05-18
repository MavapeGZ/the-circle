package com.thecircle.contracts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ContractsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractsApplication.class, args);
    }
}

