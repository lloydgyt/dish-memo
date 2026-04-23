package com.example.dish_memo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the cook history Spring Boot application.
 */
@SpringBootApplication
public class JavawebaiDemoApplication {

    /**
     * Boots the application with the active Spring profile and environment configuration.
     *
     * @param args command line arguments passed by the JVM
     */
    public static void main(String[] args) {
        SpringApplication.run(JavawebaiDemoApplication.class, args);
    }
}
