package com.game.liar.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "category")
@Getter
@Setter
public class GameCategoryProperties {
    private final Map<String, List<String>> keywords = new HashMap<>();

    public List<String> loadKeywords(String category) {
        return keywords.get(category);
    }
}
