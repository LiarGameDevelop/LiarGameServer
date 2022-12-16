package com.game.liar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.domain.GameState;
import com.game.liar.domain.Global;
import com.game.liar.domain.User;
import com.game.liar.domain.request.MessageContainer;
import com.game.liar.domain.request.RoomIdRequest;
import com.game.liar.exception.NotAllowedActionException;
import com.game.liar.exception.NotExistException;
import com.game.liar.exception.StateNotAllowedExpcetion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class GameService {
    private Map<String, GameInfo> gameManagerMap = new ConcurrentHashMap<>();
    private RoundService roundService;

    private RoomService roomService;
    ObjectMapper objectMapper = new ObjectMapper();

    public GameService(RoundService roundService, RoomService roomService) {
        this.roundService = roundService;
        this.roomService = roomService;
    }

    public boolean checkRoomExist(String roomId) {
        return gameManagerMap.containsKey(roomId);
    }

    public GameInfo getGameState(String roomId) {
        GameInfo gameInfo = gameManagerMap.get(roomId);

        if (gameInfo == null) {
            throw new NotExistException(String.format("There is no room. Room ID :%s", roomId));
        }

        return gameInfo;
    }

    public GameInfo startGame(MessageContainer request, String roomId) {
        String senderID = request.getSenderId();
        GameInfo gameInfo = gameManagerMap.get(roomId);
        if (gameInfo == null) {
            throw new NotExistException(String.format("There is no room. Room ID :%s", roomId));
        }
        if (!gameInfo.getOwnerId().equals(senderID)) {
            throw new NotAllowedActionException("You are not owner of this room");
        }
        if (gameInfo.getState() != GameState.BEFORE_START)
            throw new StateNotAllowedExpcetion("Game is not initialized");

        try {
            log.info("request.getMessage().getBody(): {}", request.getMessage().getBody());
            GameInfo.GameSettings settings = objectMapper.readValue(request.getMessage().getBody(), GameInfo.GameSettings.class);
            if (settings.getRound() == null || settings.getTurn() == null || settings.getCategory() == null) {
                throw new NullPointerException("Required parameters does exist");
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
            gameInfo.setGameSettings(settings);
            gameInfo.nextState();
            gameInfo.initialize();
            log.info("Result gameinfo : {}", gameInfo);
            return gameInfo;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public GameInfo addGame(@NotNull String roomId, @NotNull String roomOwnerId) {
        if (gameManagerMap.containsKey(roomId)) {
            log.error("The game manager already exists");
            throw new NotExistException("The game manager already exists");
        }

        GameInfo gameInfo = new GameInfo(roomId, roomOwnerId);
        gameManagerMap.put(roomId, gameInfo);
        log.debug("game manager created");
        return gameInfo;
    }

    public GameInfo getGame(@NotNull String roomId) {
        if (gameManagerMap.containsKey(roomId)) {
            return gameManagerMap.get(roomId);
        }
        throw new NotExistException(String.format("There is no room. room id : %s", roomId));
    }

    public void removeGame(@NotNull String roomName) {
        if (!gameManagerMap.containsKey(roomName)) {
            log.error("The game manager does not exists");
            return;
        }
        gameManagerMap.remove(roomName);
        log.debug("game manager destroyed");
    }

    public void clearGame() {
        gameManagerMap.clear();
    }

    public GameInfo startRound(MessageContainer request, String roomId) {
        String senderID = request.getSenderId();
        GameInfo gameInfo = gameManagerMap.get(roomId);
        if (!gameInfo.getOwnerId().equals(senderID)) {
            throw new NotAllowedActionException("You are not owner of this room");
        }
        if (gameInfo.getState() != GameState.BEFORE_ROUND)
            throw new StateNotAllowedExpcetion("Game does not start");
        if (gameInfo.getGameSettings().getRound() <= gameInfo.getRound() + 1) {
            throw new NotAllowedActionException("Round is Over. ");
        }
        gameInfo.nextRound();
        gameInfo.nextState();

        return gameInfo;
    }

    public GameInfo selectLiar(MessageContainer request, String roomId) {
        String senderID = request.getSenderId();
        GameInfo gameInfo = gameManagerMap.get(roomId);
        if (!gameInfo.getOwnerId().equals(senderID))
            throw new NotAllowedActionException("You are not owner of this room");
        if (gameInfo.getState() != GameState.SELECT_LIAR)
            throw new StateNotAllowedExpcetion("Current State is not SELECT_LIAR");

        String liarId = roundService.selectLiar(getUserIDsInRoom(roomId), roomId);
        gameInfo.selectLiar(liarId);
        gameInfo.nextState();

        return gameInfo;
    }

    public GameInfo openKeyword(MessageContainer request, String roomId) {
        String senderID = request.getSenderId();
        GameInfo gameInfo = gameManagerMap.get(roomId);
        if (!gameInfo.getOwnerId().equals(senderID))
            throw new NotAllowedActionException("You are not owner of this room");
        if (gameInfo.getState() != GameState.OPEN_KEYWORD)
            throw new StateNotAllowedExpcetion(String.format("Current State is not OPEN_KEYWORD. state:%s", gameInfo.getState()));
        roundService.openCategory(gameInfo);
        roundService.openKeyword(gameInfo);
        roundService.openTurnOrder(gameInfo);

        gameInfo.nextState();
        return gameInfo;
    }

    public GameInfo updateTurn(String senderId, String roomId) {
        GameInfo gameInfo = gameManagerMap.get(roomId);
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

    public GameInfo notifyRoundEnd(String roomId) {
        GameInfo gameInfo = gameManagerMap.get(roomId);
        if (gameInfo.getState() != GameState.IN_PROGRESS)
            throw new StateNotAllowedExpcetion(String.format("Current State is not IN_PROGRESS. state:%s", gameInfo.getState()));
        gameInfo.nextRound();
        gameInfo.resetTurn();
        gameInfo.nextState();
        return gameInfo;
    }

    public List<String> getUserIDsInRoom(String roomId) {
        return roomService.getUsers(new RoomIdRequest(roomId));
    }

    //To notify every user
    public void nextGameState(String roomId) {
        GameInfo gameInfo = gameManagerMap.get(roomId);
        gameInfo.nextState();
    }

    public void addMember(String roomId, User user) {
        GameInfo gameInfo = gameManagerMap.get(roomId);
        if (gameInfo != null) {
            log.info("room id : {} added user: {}", roomId, user);
            gameInfo.addUser(user);
        } else {
            log.error("room id does not exist");
        }
    }

    public void deleteMember(String roomId, User user) {
        GameInfo gameInfo = gameManagerMap.get(roomId);
        if (gameInfo != null) {
            log.info("room id : {} added user: {}", roomId, user);
            gameInfo.deleteUser(user);
        } else {
            log.error("room id does not exist");
        }
    }
}
