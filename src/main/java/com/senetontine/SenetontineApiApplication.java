package com.senetontine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling : necessaire pour le nettoyage periodique dans LoginRateLimiter
// (sans quoi son @Scheduled ne se declenche jamais).
@EnableScheduling
@SpringBootApplication
public class SenetontineApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(SenetontineApiApplication.class, args);
	}

}
