package com.game.liar.game.service;

import com.game.liar.game.dto.MessageContainer;
import com.game.liar.game.dto.response.*;
import com.game.liar.room.dto.UserDataDto;
import com.game.liar.game.dto.request.GameSettingsRequest;
import com.game.liar.game.dto.request.KeywordRequest;
import com.game.liar.game.dto.request.LiarDesignateRequest;
import com.game.liar.room.dto.RoomIdRequest;
import com.game.liar.exception.NotAllowedActionException;
import com.game.liar.exception.NotExistException;
import com.game.liar.exception.RequiredParameterMissingException;
import com.game.liar.exception.StateNotAllowedExpcetion;
import com.game.liar.game.domain.GameInfo;
import com.game.liar.game.domain.GameState;
import com.game.liar.game.domain.Global;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.domain.UserId;
import com.game.liar.room.service.RoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GameService {
    private Map<String, GameInfo> gameManagerMap = new ConcurrentHashMap<>();
    private RoomService roomService;

    public GameService(RoomService roomService) {
        this.roomService = roomService;
    }

    public boolean checkRoomExist(String string) {
        return gameManagerMap.containsKey(string);
    }

    public GameInfo getGameState(String string) {
        GameInfo gameInfo = gameManagerMap.get(string);

        if (gameInfo == null) {
            throw new NotExistException(String.format("There is no room. Room ID :%s", string));
        }

        return gameInfo;
    }

    public GameInfo startGame(MessageContainer request, String string) throws NotExistException, NotAllowedActionException, StateNotAllowedExpcetion {
        String senderID = request.getSenderId();
        GameInfo gameInfo = gameManagerMap.get(string);
        if (gameInfo == null) {
            throw new NotExistException(String.format("There is no room. Room ID :%s", string));
        }
        if (!gameInfo.getOwnerId().equals(senderID)) {
            throw new NotAllowedActionException("You are not owner of this room");
        }
        if (gameInfo.getState() != GameState.BEFORE_START)
            throw new StateNotAllowedExpcetion("Game is not initialized");

        log.info("request.getMessage().getBody(): {}", request.getMessage().getBody());
        GameSettingsRequest settings = (GameSettingsRequest) request.getMessage().getBody();
        if (settings == null || settings.getRound() == null || settings.getTurn() == null || settings.getCategory() == null) {
            throw new RequiredParameterMissingException("Game round, turn, and category field are required");
        }
        if (settings.getRound() <= 0 || settings.getRound() >= 6) {
            throw new NotAllowedActionException("Round can be 1 to 5");
        }
        if (settings.getTurn() <= 0 || settings.getTurn() >= 4) {
            throw new NotAllowedActionException("Turn can be 1 to 3");
        }
        if (settings.getCategory().isEmpty()) {
            throw new NotAllowedActionException("Category should contain more than 1");
        }
        log.info("request OK, settings : {}", settings);
        gameInfo.setGameSettings(settings.toEntity());
        gameInfo.nextState();
        gameInfo.initialize();
        log.info("Result gameinfo : {}", gameInfo);
        return gameInfo;
    }

    public GameInfo addGame(String roomId, String roomOwnerId) {
        if (gameManagerMap.containsKey(roomId)) {
            log.error("The game manager already exists");
            throw new NotExistException("The game manager already exists");
        }

        GameInfo gameInfo = new GameInfo(roomId, roomOwnerId);
        gameManagerMap.put(roomId, gameInfo);
        log.debug("game manager created");
        return gameInfo;
    }

    public GameInfo getGame(String string) {
        if (gameManagerMap.containsKey(string)) {
            return gameManagerMap.get(string);
        }
        throw new NotExistException(String.format("There is no room. room id : %s", string));
    }

    public void removeGame(String string) {
        if (!gameManagerMap.containsKey(string)) {
            log.error("The game manager does not exists");
            return;
        }
        gameManagerMap.remove(string);
        log.debug("game manager destroyed");
    }

    public void clearGame() {
        gameManagerMap.clear();
    }

    public GameInfo startRound(MessageContainer request, String string) {
        String senderID = request.getSenderId();
        GameInfo gameInfo = gameManagerMap.get(string);
        if (!gameInfo.getOwnerId().equals(senderID)) {
            throw new NotAllowedActionException("You are not owner of this room");
        }
        if (gameInfo.getState() != GameState.BEFORE_ROUND)
            throw new StateNotAllowedExpcetion("Current State is not BEFORE_ROUND");
        if (gameInfo.getGameSettings().getRound() < gameInfo.getRound() + 1) {
            throw new NotAllowedActionException("Round is over.");
        }
        gameInfo.nextRound();
        gameInfo.nextState();

        return gameInfo;
    }

    public GameInfo selectLiar(MessageContainer request, String string) {
        String senderID = request.getSenderId();
        GameInfo gameInfo = gameManagerMap.get(string);
        if (!gameInfo.getOwnerId().equals(senderID))
            throw new NotAllowedActionException("You are not owner of this room");
        if (gameInfo.getState() != GameState.SELECT_LIAR)
            throw new StateNotAllowedExpcetion("Current State is not SELECT_LIAR");

        String liarId = __selectLiar(getUserIdListInRoom(string.toString()), string);
        gameInfo.setLiar(liarId);
        gameInfo.nextState();

        return gameInfo;
    }

    private String __selectLiar(List<String> usersInRoom, String string) {
        Random random = new Random();
        random.setSeed(System.currentTimeMillis() + UUID.fromString(string.toString()).hashCode());
        int index = random.nextInt(usersInRoom.size());

        return usersInRoom.get(index);
    }

    public GameInfo openKeyword(MessageContainer request, String string) {
        String senderID = request.getSenderId();
        GameInfo gameInfo = gameManagerMap.get(string);
        if (!gameInfo.getOwnerId().equals(senderID))
            throw new NotAllowedActionException("You are not owner of this room");
        if (gameInfo.getState() != GameState.OPEN_KEYWORD)
            throw new StateNotAllowedExpcetion(String.format("Current State is not OPEN_KEYWORD. state:%s", gameInfo.getState()));
        makeCategory(gameInfo);
        makeKeyword(gameInfo);
        makeTurnOrder(gameInfo);

        gameInfo.nextState();
        return gameInfo;
    }

    private void makeCategory(GameInfo gameInfo) {
        List<String> valuesList = new ArrayList<>(gameInfo.getSelectedByRoomOwnerCategory().keySet());
        Collections.shuffle(valuesList);
        gameInfo.setCurrentRoundCategory(valuesList.get(0));
        log.info("current round category : {}", gameInfo.getCurrentRoundCategory());
    }

    private void makeKeyword(GameInfo gameInfo) {
        List<String> valuesList = new ArrayList<>(gameInfo.getSelectedByRoomOwnerCategory().get(gameInfo.getCurrentRoundCategory()));
        Collections.shuffle(valuesList);
        gameInfo.setCurrentRoundKeyword(valuesList.get(0));
        log.info("current round keyword : {}", gameInfo.getCurrentRoundKeyword());
    }

    private void makeTurnOrder(GameInfo gameInfo) {
        List<String> valuesList = gameInfo.getGameUserList().stream().map(UserDataDto::getUserId).collect(Collectors.toList());
        Collections.shuffle(valuesList);
        gameInfo.setTurnOrder(valuesList);
        log.info("current round turn order : {}", gameInfo.getTurnOrder());
    }

    public GameInfo updateTurn(String senderId, String string) {
        GameInfo gameInfo = gameManagerMap.get(string);
        if (gameInfo.getState() != GameState.IN_PROGRESS)
            throw new StateNotAllowedExpcetion(String.format("Current State is not IN_PROGRESS. state:%s", gameInfo.getState()));
        if (gameInfo.getTurn() >= gameInfo.getGameSettings().getTurn() * gameInfo.getTurnOrder().size()) {
            //log.info("turn cannot be larger than total turn. turn:{}", gameInfo.getTurn());
            throw new NotAllowedActionException(String.format("turn cannot be larger than total turn. turn:%d", gameInfo.getTurn()));
        }
        if (gameInfo.getTurn() < 0) {
            if (!senderId.equals(Global.SERVER_ID))
                throw new RuntimeException("It's not your turn");
        } else {
            if (!gameInfo.getCurrentTurnId().equals(senderId))
                throw new RuntimeException("It's not your turn");
        }
        gameInfo.nextTurn();
        return gameInfo;
    }

    public GameInfo notifyFindingLiarEnd(String string) {
        GameInfo gameInfo = gameManagerMap.get(string);
        if (gameInfo.getState() != GameState.IN_PROGRESS)
            throw new StateNotAllowedExpcetion(String.format("Current State is not IN_PROGRESS. state:%s", gameInfo.getState()));
        gameInfo.resetTurn();
        gameInfo.nextState();
        return gameInfo;
    }

    public List<String> getUserIdListInRoom(String string) {
        return roomService.getUsersId(new RoomIdRequest(string));
    }

    //To notify every user
    public void nextGameState(String string) {
        GameInfo gameInfo = gameManagerMap.get(string);
        gameInfo.nextState();
    }

    public void addMember(String roomId, UserDataDto gameUser) {
        GameInfo gameInfo = gameManagerMap.get(roomId);
        if (gameInfo != null) {
            log.info("[addMember] room id : {} added user: {}", roomId, gameUser);
            gameInfo.addUser(gameUser);
        } else {
            log.error("room id does not exist");
        }
    }

    public void deleteMember(String roomId, UserDataDto gameUser) {
        GameInfo gameInfo = gameManagerMap.get(roomId);
        if (gameInfo != null) {
            log.info("[deleteMember] room id : {} removed user: {}", roomId, gameUser);
            gameInfo.deleteUser(gameUser);
        } else {
            log.error("room id does not exist");
        }
    }

    public GameInfo voteLiar(MessageContainer request, String string) {
        GameInfo gameInfo = gameManagerMap.get(string);
        String senderId = request.getSenderId();
        synchronized (this) {
            if (gameInfo.getState() != GameState.VOTE_LIAR) {
                throw new StateNotAllowedExpcetion(String.format("Current State is not VOTE_LIAR. state:%s", gameInfo.getState()));
            }
            if (gameInfo.checkVoteCompleted(senderId)) {
                throw new NotAllowedActionException("Vote finished");
            }
            LiarDesignateRequest liarDesignate = (LiarDesignateRequest) request.getMessage().getBody();
            if (liarDesignate.getLiar().equals("")) {
                log.debug("vote timeout from room id : {}, GameInfo: {}", string, gameInfo);
            } else if (!gameInfo.isUserInTheRoom(liarDesignate.getLiar())) {
                throw new NotExistException("Designated user does not exist");
            }
            gameInfo.addVoteResult(senderId, liarDesignate.getLiar());
            return gameInfo;
        }
    }

    public VoteResult getMostVoted(String string) {
        GameInfo gameInfo = gameManagerMap.get(string);
        List<Map.Entry<String, Long>> mostVoted = gameInfo.getMostVotedUserIdAndCount();
        return VoteResult.builder()
                .voteResult(gameInfo.getVoteResult())
                .mostVoted(mostVoted)
                .build();
    }

    public OpenLiarResponse openLiar(MessageContainer request, String string) {
        GameInfo gameInfo = gameManagerMap.get(string);
        String senderId = request.getSenderId();
        if (gameInfo.getState() != GameState.OPEN_LIAR) {
            throw new StateNotAllowedExpcetion(String.format("Current State is not OPEN_LIAR. state:%s", gameInfo.getState()));
        }
        if (!senderId.equals(gameInfo.getOwnerId())) {
            throw new NotAllowedActionException("Only room owner can open liar");
        }
        gameInfo.nextState();
        boolean isAnswer = gameInfo.isUsersMatchLiar();
        return new OpenLiarResponse(gameInfo.getLiarId(), isAnswer, gameInfo.getState());
    }

    public LiarAnswerResponse checkKeywordCorrect(MessageContainer request, String string) {
        GameInfo gameInfo = gameManagerMap.get(string);
        String senderId = request.getSenderId();
        if (gameInfo.getState() != GameState.LIAR_ANSWER) {
            throw new StateNotAllowedExpcetion(String.format("Current State is not LIAR_ANSWER. state:%s", gameInfo.getState()));
        }
        if (!senderId.equals(gameInfo.getLiarId())) {
            throw new NotAllowedActionException("Only liar can request to check keyword is right");
        }
        KeywordRequest keywordRequest = (KeywordRequest) request.getMessage().getBody();
        gameInfo.nextState();
        gameInfo.setLiarAnswer(gameInfo.getCurrentRoundKeyword().equals(keywordRequest.getKeyword()));
        return new LiarAnswerResponse(gameInfo.getState(), gameInfo.isLiarAnswer(), gameInfo.getCurrentRoundKeyword());
    }

    public ScoreboardResponse notifyScores(MessageContainer request, String string) {
        GameInfo gameInfo = gameManagerMap.get(string);
        String senderId = request.getSenderId();
        if (gameInfo.getState() != GameState.PUBLISH_SCORE) {
            throw new StateNotAllowedExpcetion(String.format("Current State is not PUBLISH_SCORE. state:%s", gameInfo.getState()));
        }
        if (!senderId.equals(gameInfo.getOwnerId())) {
            throw new NotAllowedActionException("Only Room owner can query user scores");
        }
        gameInfo.updateScoreBoard();
        return new ScoreboardResponse(gameInfo.getScoreboard());
    }

    public GameInfo notifyRoundEnd(String string) {
        GameInfo gameInfo = gameManagerMap.get(string);
        if (gameInfo.getState() != GameState.PUBLISH_SCORE)
            throw new StateNotAllowedExpcetion(String.format("Current State is not PUBLISH_SCORE. state:%s", gameInfo.getState()));
        if (gameInfo.getRound().intValue() == gameInfo.getGameSettings().getRound().intValue())
            gameInfo.nextState();
        else
            gameInfo.nextLoop();
        return gameInfo;
    }

    public void resetLiarInfo(String string) {
        GameInfo gameInfo = gameManagerMap.get(string);
        gameInfo.resetLiarInfo();
    }

    public RankingsResponse publishRankings(MessageContainer request, String string) {
        GameInfo gameInfo = gameManagerMap.get(string);
        String senderId = request.getSenderId();
        if (gameInfo.getState() != GameState.PUBLISH_RANKINGS)
            throw new StateNotAllowedExpcetion(String.format("Current State is not PUBLISH_RANKINGS. state:%s", gameInfo.getState()));
        if (!senderId.equals(gameInfo.getOwnerId())) {
            throw new NotAllowedActionException("Only Room owner can query publish rankings");
        }
        //{"rankings":[{"id":"id1","score":12},{"id":"id2","score":11}]}
        return new RankingsResponse(gameInfo.getScoreboard().entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .map(entry -> new RankingsResponse.RankingInfo(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
    }

    public List<String> getNotVoteUserList(String string) {
        GameInfo gameInfo = gameManagerMap.get(string);
        List<String> notVoteUserList = new ArrayList<>();
        log.info("gameInfo.getUserList : {}", gameInfo.getGameUserList());
        log.info("gameInfo.getVoteResult : {}", gameInfo.getVoteResult());
        for (UserDataDto gameUser : gameInfo.getGameUserList()) {
            if (gameInfo.getVoteResult().get(gameUser.getUserId()) == null)
                notVoteUserList.add(gameUser.getUserId());
        }
        return notVoteUserList;
    }

    public GameCategoryResponse getGameCategory(String roomId) {
        GameInfo gameInfo = gameManagerMap.get(roomId);

        if (gameInfo == null) {
            throw new NotExistException(String.format("There is no room. Room ID :%s", roomId));
        }
        return new GameCategoryResponse(gameInfo.getCategory());
    }

    public void cancelTurnTimer(String string) {
        gameManagerMap.get(string).cancelTurnTimer();
    }

    public void cancelVoteTimer(String string) {
        gameManagerMap.get(string).cancelVoteTimer();
    }

    public void cancelAnswerTimer(String string) {
        gameManagerMap.get(string).cancelAnswerTimer();
    }

    public void resetGame(String string) {
        GameInfo gameInfo = gameManagerMap.get(string);
    }
}
