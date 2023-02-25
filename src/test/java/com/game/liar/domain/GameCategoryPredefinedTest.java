package com.game.liar.domain;

import com.game.liar.game.config.GameCategoryProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest({GameCategoryProperties.class})
@Disabled
class GameCategoryPredefinedTest {
    @Autowired
    GameCategoryProperties predefined;
    @Test
    public void init () throws Exception{
        List<String> foodKeywords= predefined.loadKeywords("food");
        assertThat(foodKeywords).contains("pizza", "tteokbokki", "bibimbab", "chicken");
    }
}