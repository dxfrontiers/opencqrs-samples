package com.example.cqrs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OptimisticLockingDemoApp {
    public static void main(String[] args) {
        SpringApplication.run(OptimisticLockingDemoApp.class, args);
    }
}
