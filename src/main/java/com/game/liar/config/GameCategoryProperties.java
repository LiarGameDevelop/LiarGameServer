package com.game.liar.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "category")
@Getter
@Setter
public class GameCategoryProperties {
    private final Map<String, List<String>> keywords = new HashMap<>();

    public List<String> loadKeywords(String category) {
        return keywords.get(category);
    }

    @Setter
    @Getter
    public static class GameKeyword {
        String keyword;
    }
}
