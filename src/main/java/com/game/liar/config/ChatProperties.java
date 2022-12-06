package com.game.liar.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "chat")
@Getter
@Setter
public class ChatProperties {
    private Destinations destinations;

    @Getter
    @Setter
    public static class Destinations {
        private String login;
        private String logout;
    }
}
