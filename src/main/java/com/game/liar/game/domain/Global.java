package com.game.liar.game.domain;

import java.util.HashMap;
import java.util.Map;

public class Global {
    /**
     * Server -> Client method
     **/
    public static final String NOTIFY_GAME_STARTED = "notifyGameStarted";
    public static final String NOTIFY_ROUND_STARTED = "notifyRoundStarted";
    public static final String NOTIFY_LIAR_SELECTED = "notifyLiarSelected";
    public static final String NOTIFY_KEYWORD_OPENED = "notifyKeywordOpened";
    public static final String NOTIFY_TURN_TIMEOUT = "notifyTurnTimeout";
    public static final String NOTIFY_TURN = "notifyTurn";
    public static final String NOTIFY_FINDING_LIAR_END = "notifyFindingLiarEnd";
    public static final String NOTIFY_VOTE_RESULT = "notifyVoteResult";
    public static final String NOTIFY_LIAR_OPEN_REQUEST = "notifyLiarOpenRequest";

    public static final String NOTIFY_NEW_VOTE_NEEDED = "notifyNewVoteNeeded";
    public static final String NOTIFY_VOTE_TIMEOUT = "notifyVoteTimeout";
    public static final String NOTIFY_LIAR_OPENED = "notifyLiarOpened";
    public static final String NOTIFY_LIAR_ANSWER_NEEDED = "notifyLiarAnswerNeeded";
    public static final String NOTIFY_LIAR_ANSWER_CORRECT = "notifyLiarAnswerCorrect";
    public static final String NOTIFY_LIAR_ANSWER_TIMEOUT = "notifyLiarAnswerTimeOut";
    public static final String NOTIFY_SCORES = "notifyScores";
    public static final String NOTIFY_ROUND_END = "notifyRoundEnd";
    public static final String NOTIFY_RANKINGS_PUBLISHED = "notifyRankingsPublished";
    public static final String NOTIFY_GAME_END = "notifyGameEnd";
    public static final String NOTIFY_GAME_STATE = "notifyGameState";
    public static final String NOTIFY_GAME_CATEGORY = "notifyGameCategory";
    /**
     * Client -> Server method
     **/
    public static final String START_GAME = "startGame";
    public static final String START_ROUND = "startRound";
    public static final String SELECT_LIAR = "selectLiar";
    public static final String OPEN_KEYWORD = "openKeyword";
    public static final String REQUEST_TURN_FINISH = "requestTurnFinished";
    public static final String VOTE_LIAR = "voteLiar";
    public static final String OPEN_LIAR = "openLiar";
    public static final String LIAR_ANSWER = "liarAnswer";
    public static final String CHECK_KEYWORD_CORRECT = "checkKeywordCorrect";
    public static final String OPEN_SCORES = "openScores";
    public static final String END_ROUND = "endRound";
    public static final String PUBLISH_RANKINGS = "publishRankings";
    public static final String GET_GATE_STATE = "getGameState";
    public static final String GET_GAME_CATEGORY = "getGameCategory";

    //Common
    public static final String SERVER_ID = "SERVER";

    public enum MessageType {
        MESSAGE,
        DESCRIPTION
    }

    public static Map<String, String> apiRequestMapper = new HashMap<String, String>() {{
        put(START_GAME, NOTIFY_GAME_STARTED);
        put(START_ROUND, NOTIFY_ROUND_STARTED);
        put(SELECT_LIAR, NOTIFY_LIAR_SELECTED);
        put(OPEN_KEYWORD, NOTIFY_KEYWORD_OPENED);
        put(REQUEST_TURN_FINISH, NOTIFY_FINDING_LIAR_END);
        put(VOTE_LIAR, NOTIFY_VOTE_RESULT);
        put(OPEN_LIAR, NOTIFY_LIAR_OPENED);
        put(CHECK_KEYWORD_CORRECT, NOTIFY_LIAR_ANSWER_CORRECT);
        put(OPEN_SCORES, NOTIFY_SCORES);
        put(PUBLISH_RANKINGS, NOTIFY_RANKINGS_PUBLISHED);
        put(GET_GATE_STATE, NOTIFY_GAME_STATE);
        put(GET_GAME_CATEGORY, NOTIFY_GAME_CATEGORY);
    }};
}