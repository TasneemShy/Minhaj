package com.example.minhaj;

import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableJpaAuditing
public class MinhajApplication {
    public static void main(String[] args) {
        SpringApplication.run(MinhajApplication.class, args);
    }
}