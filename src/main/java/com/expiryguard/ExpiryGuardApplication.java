package com.expiryguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExpiryGuardApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExpiryGuardApplication.class, args);
    }
}