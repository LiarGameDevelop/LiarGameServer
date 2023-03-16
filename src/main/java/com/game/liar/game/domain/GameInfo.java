package com.game.liar.game.domain;


import com.game.liar.exception.NotExistException;
import com.game.liar.room.dto.UserDataDto;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@ToString
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameInfo {
    @Getter
    private boolean turnTimerRunning;
    @Getter
    private boolean voteTimerRunning;
    @Getter
    private boolean answerTimerRunning;
    @Enumerated
    private GameState state;

    public GameState getState() {
        synchronized (this) {
            return state;
        }
    }

    @Id
    @Getter
    private String roomId;
    @Getter
    private String ownerId;

    @Getter
    @Embedded
    private GameSettings gameSettings;

    //Current round info
    @Getter
    private Integer currentRound;
    @Getter
    private Integer currentTurn;
    @Getter
    private String liarId;
    @Getter
    private String currentRoundCategory;
    @Getter
    private String currentRoundKeyword;
    /**
     * TODO: 유저가 중간에 나가는경우도 고려해야함
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "turn_order",
            joinColumns = @JoinColumn(name = "game_id")
    )
    @OrderColumn(name = "line_index")
    @Getter
    private List<String> turnOrder = new ArrayList<>(); //밸류 타입

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "vote_result",
            joinColumns = @JoinColumn(name = "game_id")
    )
    @MapKeyColumn(name = "vote_from")
    @Getter
    private Map<String, String> voteResult = new HashMap<>(); //밸류타입
    @Getter
    private Integer voteCount = 0;

    @Getter
    private boolean liarAnswer;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "score_board",
            joinColumns = @JoinColumn(name = "game_id")
    )
    @MapKeyColumn(name = "user_id")
    @Getter
    private Map<String, Integer> scoreboard = new HashMap<>(); //밸류타입

    public String getCurrentTurnId() {
        synchronized (this) {
            log.info("[getCurrentTurnId] turn : {} turnOrder{}", currentTurn, turnOrder);
            if (currentTurn >= turnOrder.size())
                return turnOrder.get(currentTurn % turnOrder.size());
            return turnOrder.get(currentTurn);
        }
    }

    public void initialize(Map<String, List<String>> subjects, List<String> categoryList) {
        currentRound = 0;
        currentTurn = -1;
        gameSettings.initializeCategory(subjects, categoryList);
    }

    public void nextRound() {
        currentRound++;
    }

    public void nextTurn() {
        synchronized (this) {
            currentTurn++;
            log.info("current turn is {}, room id : {}", currentTurn, roomId);
        }
    }

    public void resetTurn() {
        currentTurn = -1;
        log.info("current turn is {}, room id : {}", currentTurn, roomId);
    }

    public void selectLiar(String liar) {
        liarId = liar;
    }

    public void addUser(UserDataDto gameUser) {
        scoreboard.put(gameUser.getUserId(), 0);
    }

    public void deleteUser(UserDataDto gameUser) {
        scoreboard.remove(gameUser.getUserId());
    }

    public void cancelTurnTimer() {
        log.info("cancel turn timer [room:{}]", roomId);
        turnTimerRunning = false;
    }

    public void cancelVoteTimer() {
        log.info("cancel vote timer [room:{}]", roomId);
        voteTimerRunning = false;
    }

    public void cancelAnswerTimer() {
        log.info("cancel liar answer timer [room:{}]", roomId);
        answerTimerRunning = false;
    }

    public void startTurnTimer() {
        log.info("start turn timer [room:{}]", roomId);
        turnTimerRunning = true;
    }

    public void startVoteTimer() {
        log.info("start vote timer [room:{}]", roomId);
        voteTimerRunning = true;
    }

    public void startAnswerTimer() {
        log.info("start liar answer timer [room:{}]", roomId);
        answerTimerRunning = true;
    }

    public boolean isLastTurn() {
        return currentTurn == (turnOrder.size() * gameSettings.getTurn());
    }

    public void addVoteResult(String from, String liarDesignatedId) {
        voteResult.put(from, liarDesignatedId);
        voteCount++;
        log.info("user[{}] -> liar [{}]. voteCount : {}", from, liarDesignatedId, voteCount);
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
        log.info("[roomId:{}] scoreBoard : {}", roomId, scoreboard);
    }

    public boolean isUsersMatchLiar() {
        if (getMostVotedUserIdAndCount() == null)
            return false;
        return getMostVotedUserIdAndCount().get(0).getKey().equals(liarId);
    }

    public boolean voteFinished() {
        log.info("turn order form voteFinished:{}, vote count :{},vote result :{}", turnOrder, voteCount,voteResult);
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

    public List<String> getCategory() {
        return new ArrayList<>(gameSettings.getSelectedByRoomOwnerCategory().keySet());
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

    public void initializeRoundKeyword(String keyword) {
        currentRoundKeyword = keyword;
    }

    public void initializeRoundCategory(String category) {
        currentRoundCategory = category;
    }

    public void initializeRoundTurnOrder(List<String> valuesList) {
        if (valuesList == null || valuesList.isEmpty())
            throw new NotExistException("[initializeRoundTurnOrder] value list not exist");
        turnOrder = valuesList;
    }

    public void setLiarAnswer(boolean answer) {
        liarAnswer = answer;
    }

    public void setGameSettings(GameSettings settings) {
        this.gameSettings = settings;
    }
}
