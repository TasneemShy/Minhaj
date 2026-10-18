package com.example.minhaj;

import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
public class MinhajApplication {
    public static void main(String[] args) {
        SpringApplication.run(MinhajApplication.class, args);
    }
}