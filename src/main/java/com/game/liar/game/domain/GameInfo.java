package com.game.liar.game.domain;


import com.fasterxml.jackson.annotation.JsonTypeName;
import com.game.liar.game.dto.MessageBody;
import com.game.liar.room.dto.UserDataDto;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@ToString
public class GameInfo {
    @Getter
    private Timer turnTimer;
    @Getter
    private Timer voteTimer;
    @Getter
    private Timer answerTimer;
    @Getter
    @Setter
    private TimerTask turnTask;
    @Getter
    @Setter
    private TimerTask voteTask;
    @Getter
    @Setter
    private TimerTask answerTask;

    private GameState state;

    public GameState getState() {
        synchronized (this) {
            return state;
        }
    }

    @Getter
    private String roomId;
    @Getter
    private String ownerId;
    @Getter
    private final List<UserDataDto> gameUserList = new ArrayList<>();

    @Getter
    @Setter
    private GameSettings gameSettings;

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

    @Getter
    @Setter
    private Map<String, String> voteResult = new ConcurrentHashMap<>();
    @Getter
    private Integer voteCount = 0;

    @Getter
    @Setter
    private boolean liarAnswer;

    @Getter
    private Map<String, Integer> scoreboard = new ConcurrentHashMap<>();

    public String getCurrentTurnId() {
        synchronized (this) {
            log.info("[getCurrentTurnId] turn : {}", turn);
            if (turn >= turnOrder.size())
                return turnOrder.get(turn % turnOrder.size());
            return turnOrder.get(turn);
        }
    }

    public void initialize(Map<String, List<String>> subjects, List<String> categoryList) {
        round = 0;
        turn = -1;
        initializeCategory(subjects, categoryList);
    }

    public void nextRound() {
        round++;
    }

    public void nextTurn() {
        synchronized (this) {
            turn++;
            log.info("current turn is {}, room id : {}", turn, roomId);
        }
    }

    public void resetTurn() {
        turn = -1;
    }

    public void setLiar(String liar) {
        liarId = liar;
    }

    public void addUser(UserDataDto gameUser) {
        gameUserList.add(gameUser);
        scoreboard.put(gameUser.getUserId(), 0);
    }

    public void deleteUser(UserDataDto gameUser) {
        gameUserList.remove(gameUser);
        scoreboard.remove(gameUser.getUserId());
    }

    public boolean isUserInTheRoom(String userId) {
        return gameUserList.stream().anyMatch(user -> user.getUserId().equals(userId));
    }

    public void cancelTurnTimer() {
        if (turnTimer == null) return;
        log.info("cancel turn timer [room:{}]", roomId);
        turnTimer.cancel();
        turnTask.cancel();
    }

    public void cancelVoteTimer() {
        if (voteTimer == null) return;
        log.info("cancel vote timer [room:{}]", roomId);
        voteTimer.cancel();
        voteTask.cancel();
    }

    public void cancelAnswerTimer() {
        if (answerTimer == null) return;
        log.info("cancel liar answer timer [room:{}]", roomId);
        answerTimer.cancel();
        answerTask.cancel();
    }

    public void scheduleTurnTimer(long delay) {
        turnTimer = new Timer();
        turnTimer.schedule(turnTask, delay);
    }

    public void scheduleVoteTimer(long delay) {
        voteTimer = new Timer();
        voteTimer.schedule(voteTask, delay);
    }

    public void scheduleAnswerTimer(long delay) {
        answerTimer = new Timer();
        answerTimer.schedule(answerTask, delay);
    }

    public boolean isLastTurn() {
        synchronized (this) {
            return turn == turnOrder.size() * gameSettings.turn;
        }
    }

    public void addVoteResult(String from, String liarDesignatedId) {
        log.info("user[{}] -> liar [{}]", from, liarDesignatedId);
        voteResult.put(from, liarDesignatedId);

        voteCount++;
    }

    public boolean checkVoteCompleted(String from) {
        return voteResult.containsKey(from);
    }

    public void resetVoteResult() {
        voteResult.clear();
        voteCount = 0;
    }

    public void resetScoreBoard() {
        for (Map.Entry<String, Integer> item : scoreboard.entrySet()) {
            item.setValue(0);
        }
    }

    public void updateScoreBoard() {
        if (isUsersMatchLiar()) {
            //시민들이 라이어를 맞췄으면 시민들의 점수를 더한다.
            for (Map.Entry<String, Integer> item : scoreboard.entrySet()) {
                if (!item.getKey().equals(liarId)) {
                    //TODO:make const score
                    item.setValue(item.getValue() + 1);
                }
            }
        } else {
            //시민들이 못맞췄으면 라이어의 점수를 더한다.
            Integer score = scoreboard.get(liarId);
            scoreboard.replace(liarId, score + 2);

            //잘찍은 시민들은 점수를 더한다.
            scoreboard.entrySet().stream().filter(entry ->
                            !entry.getKey().equals(liarId) && getVoteResult().get(entry.getKey()).equals(liarId))
                    .forEach(item -> item.setValue(item.getValue() + 1));
        }
        if (isLiarAnswer()) {
            //이번턴에 라이어의 점수를 X점 더한다.
            Integer score = scoreboard.get(liarId);
            scoreboard.replace(liarId, score + 1);
        }
        log.info("scoreBoard : {}", scoreboard);
    }

    public boolean isUsersMatchLiar() {
        if (getMostVotedUserIdAndCount() == null)
            return false;
        return getMostVotedUserIdAndCount().get(0).getKey().equals(liarId);
    }

    public synchronized boolean voteFinished() {
        return voteCount == turnOrder.size();
    }

    public List<Map.Entry<String, Long>> getMostVotedUserIdAndCount() {
        Map<String, Long> voteCountByDesignatedId =
                voteResult.values().stream()
                        .filter(value -> !value.equals(""))
                        .collect(Collectors.groupingBy(liar -> liar, Collectors.counting()));
        log.info("voteResult :{} from [{}]. counting :{}", voteResult, roomId, voteCountByDesignatedId);
        Optional<Map.Entry<String, Long>> optional = voteCountByDesignatedId.entrySet().stream().max(Map.Entry.comparingByValue());
        if (optional.isPresent())
            return voteCountByDesignatedId.entrySet().stream()
                    .filter(entry -> entry.getValue().longValue() == optional.get().getValue().longValue()).collect(Collectors.toList());
        else
            return null;
    }

    public void resetLiarInfo() {
        liarId = null;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonTypeName("gameSettings")
    public static class GameSettings extends MessageBody {
        private Integer round;
        private Integer turn;
        private List<String> category;

        @Override
        public String toString() {
            return "{" + "\"round\":" + round + ", \"turn\":" + turn + ", \"category\": [" + category + "]}";
        }
    }

    public void initializeCategory(Map<String, List<String>> subjects, List<String> categoryList) {
        log.info("subjects = {}, categories = {}", subjects, categoryList);
        selectedByRoomOwnerCategory = new ConcurrentHashMap<>();

        for (String category : categoryList) {
            if (!gameSettings.getCategory().contains(category))
                continue;
            List<String> predefinedKeywords = subjects.get(category);
            if (predefinedKeywords != null) {
                selectedByRoomOwnerCategory.put(category, predefinedKeywords);
            }
        }
    }

    public List<String> getCategory() {
        return new ArrayList<>(selectedByRoomOwnerCategory.keySet());
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

    public GameState nextLoop() {
        return state = state.loop();
    }

    public GameState setState(GameState gameState) {
        return state = gameState;
    }
}
