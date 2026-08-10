package com.saloon.songbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SongBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(SongBackendApplication.class, args);

        System.out.println("song application started ");
    }
}
