package com.game.liar.service;


import com.game.liar.config.GameCategoryProperties;
import com.game.liar.domain.GameState;
import com.game.liar.utils.BeanUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ToString
public class GameInfo {
    @Getter
    private GameState state;
    @Getter
    private String roomId;
    @Getter
    private String ownerId;

    @Getter
    @Setter
    private GameSettings gameSettings;

    private static GameCategoryProperties gameCategoryProperties;
    @Getter
    private Integer round = 0;
    @Getter
    private Integer turn = 0;
    @Getter
    private Map<String, List<String>> category;

    @Getter
    private String liarId;

    public void initialize() {
        round = 0;
        turn = 0;
        initializeCategory(gameSettings.getCategory());
    }

    public void nextRound(){
        round++;
    }

    public void nextTurn(){
        turn++;
    }
    public void selectLiar(String liar){ liarId=liar;}

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder(builderMethodName = "gameSettingsBuilder")
    public static class GameSettings {
        private Integer round;
        private Integer turn;
        private List<String> category;

        @Override
        public String toString() {
            return "{" +
                    "\"round\":" + round +
                    ", \"turn\":" + turn +
                    ", \"category\": [" + category +
                    "]}";
        }
    }

    public void initializeCategory(List<String> categoryList) {
        category = new ConcurrentHashMap<>();
        if (gameCategoryProperties == null)
            gameCategoryProperties = (GameCategoryProperties) BeanUtils.getBean(GameCategoryProperties.class);
        for (String subject : categoryList) {
            List<String> predefinedKeywords = gameCategoryProperties.loadKeywords(subject);
            if (predefinedKeywords != null) {
                category.put(subject, predefinedKeywords);
            }
        }
    }

    public void addCustomCategory(String subject, List<String> keywordList) {
        category.put(subject, keywordList);
    }

    public GameInfo(String roomId, String ownerId) {
        this.roomId = roomId;
        this.ownerId = ownerId;
        state = GameState.BEFORE_START;
    }

    public GameState nextState() {
        return state = state.next();
    }

    public GameState setState(GameState gameState){
        return state = gameState;
    }
}
