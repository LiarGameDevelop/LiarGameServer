package com.game.liar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class LiarApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiarApplication.class, args);
    }

}
