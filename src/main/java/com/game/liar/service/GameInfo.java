package com.game.liar.service;


import com.game.liar.config.GameCategoryProperties;
import com.game.liar.domain.GameState;
import com.game.liar.domain.User;
import com.game.liar.utils.BeanUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ToString
public class GameInfo {
    @Getter
    private Timer turnTimer = new Timer();
    @Getter
    private GameState state;
    @Getter
    private String roomId;
    @Getter
    private String ownerId;
    @Getter
    private final List<User> userList = new ArrayList<>();

    @Getter
    @Setter
    private GameSettings gameSettings;

    private static GameCategoryProperties gameCategoryProperties;

    //Current round info
    @Getter
    private Integer round;
    @Getter
    private Integer turn;
    @Getter
    private Map<String, List<String>> selectedByRoomOwnerCategory;

    @Getter
    private String liarId;
    @Getter
    @Setter
    private String currentRoundCategory;
    @Getter
    @Setter
    private String currentRoundKeyword;
    /**
     * TODO: 유저가 중간에 나가는경우도 고려해야함
     */
    @Getter
    @Setter
    private List<String> turnOrder;

    public String getCurrentTurnId() {
        if (turn >= turnOrder.size())
            return turnOrder.get(turn % turnOrder.size());
        return turnOrder.get(turn);
    }

    public void initialize() {
        round = 0;
        turn = -1;
        initializeCategory(gameSettings.getCategory());
    }

    public void nextRound() {
        round++;
    }

    public void nextTurn() {
        turn++;
        log.info("current turn is {}, room id : {}", turn, roomId);
    }

    public void resetTurn() {
        turn = -1;
    }

    public void selectLiar(String liar) {
        liarId = liar;
    }

    public void addUser(User user) {
        userList.add(user);
    }

    public void deleteUser(User user) {
        userList.remove(user);
    }

    public void cancelTimer() {
        log.info("cancel timer");
        turnTimer.cancel();
    }

    public void scheduleTimer(TimerTask task, long delay) {
        turnTimer = new Timer();
        turnTimer.schedule(task, delay);
    }

    public boolean isLastTurn() {
        if (turn == turnOrder.size() * gameSettings.turn)
            return true;
        return false;
    }

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
            return "{" + "\"round\":" + round + ", \"turn\":" + turn + ", \"category\": [" + category + "]}";
        }
    }

    public void initializeCategory(List<String> categoryList) {
        selectedByRoomOwnerCategory = new ConcurrentHashMap<>();
        if (gameCategoryProperties == null)
            gameCategoryProperties = (GameCategoryProperties) BeanUtils.getBean(GameCategoryProperties.class);
        for (String subject : categoryList) {
            List<String> predefinedKeywords = gameCategoryProperties.loadKeywords(subject);
            if (predefinedKeywords != null) {
                selectedByRoomOwnerCategory.put(subject, predefinedKeywords);
            }
        }
    }

    public void addCustomCategory(String subject, List<String> keywordList) {
        selectedByRoomOwnerCategory.put(subject, keywordList);
    }

    public GameInfo(String roomId, String ownerId) {
        this.roomId = roomId;
        this.ownerId = ownerId;
        state = GameState.BEFORE_START;
    }

    public GameState nextState() {
        return state = state.next();
    }

    public GameState setState(GameState gameState) {
        return state = gameState;
    }
}
