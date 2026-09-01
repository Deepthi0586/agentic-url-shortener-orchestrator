package com.saigangili.shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ShortenerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortenerServiceApplication.class, args);
    }
}
