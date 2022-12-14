package com.game.liar.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.domain.GameState;
import com.game.liar.domain.Global;
import com.game.liar.domain.request.MessageContainer;
import com.game.liar.domain.response.GameStateResponse;
import com.game.liar.domain.response.RoundInfoResponse;
import com.game.liar.service.GameInfo;
import com.game.liar.service.GameService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

import static com.game.liar.domain.Global.*;

@RestController
@Slf4j
public class GameController {

    private static ObjectMapper objectMapper = new ObjectMapper();

    private SimpMessagingTemplate messagingTemplate;
    private GameService gameService;

    public GameController(SimpMessagingTemplate messagingTemplate, GameService gameService) {
        this.messagingTemplate = messagingTemplate;
        this.gameService = gameService;
    }

    //TODO: use validation
    @MessageMapping("/system/private/{roomId}")
    public void sendHostMessage(@Payload MessageContainer request, @DestinationVariable("roomId") String roomId) {
        log.info("message from room id({}) : {}", roomId, request);
        if (gameService.checkRoomExist(roomId)) {
            String method = request.getMessage().getMethod();
            processMapper.get(method).process(request, roomId);
        } else {
            log.error("mapped room id does not exist");
        }
    }

    @MessageMapping("/system/public/{roomId}")
    public void sendPublicMessage(@Valid @Payload MessageContainer request, @DestinationVariable("roomId") String roomId) {
        if (gameService.checkRoomExist(roomId)) {
            String method = request.getMessage().getMethod();
            processMapper.get(method).process(request, roomId);
        }
    }

    public void addRoom(String roomName, String ownerId) {
        gameService.addGame(roomName, ownerId);
    }

    @FunctionalInterface
    public interface ProcessGame {
        void process(MessageContainer request, String roomId);
    }

    ProcessGame getGameState = (request, roomId) -> {
        GameInfo gameInfo = gameService.getGameState(roomId);
        try {
            GameState state=gameInfo.getState();
            sendHostMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_STATE, objectMapper.writeValueAsString(new GameStateResponse(gameInfo.getState()))), roomId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    };

    ProcessGame startGame = (request, roomId) -> {
        GameInfo gameInfo = gameService.startGame(request, roomId);
        try {
            sendHostMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_STARTED, objectMapper.writeValueAsString(new GameStateResponse(gameInfo.getState()))), roomId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    };

    ProcessGame startRound = (request, roomId) -> {
        GameInfo gameInfo = gameService.startRound(request, roomId);
        try {
            sendHostMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_ROUND_STARTED, objectMapper.writeValueAsString(new RoundInfoResponse(gameInfo.getState(), gameInfo.getRound()))), roomId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    };

    ProcessGame selectLiar = (request, roomId) -> {
        GameInfo gameInfo = gameService.selectLiar(request, roomId);
        String body = "";
        for (String userId : gameService.getUsersInRoom(roomId)) {
            boolean liar = userId.equals(gameInfo.getLiarId());
            try {
                body = objectMapper.writeValueAsString(liar);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_LIAR_SELECTED, body), request.getSenderId());
        }
        gameService.nextGameState(roomId);
    };

    private final Map<String, ProcessGame> processMapper = new HashMap<String, ProcessGame>() {
        {
            put(Global.GET_GATE_STATE, getGameState);
            put(Global.START_GAME, startGame);
            put(Global.START_ROUND, startRound);
            put(Global.SELECT_LIAR, selectLiar);
            put(Global.OPEN_KEYWORD, startGame);
            put(Global.IN_PROGRESS, startGame);
            put(Global.VOTE_LIAR, startGame);
            put(Global.OPEN_LIAR, startGame);
            put(Global.REQUEST_LIAR_ANSWER, startGame);
            put(Global.CHECK_LIAR_ANSWER, startGame);
            put(Global.PUBLISH_SCORE, startGame);
        }
    };

    public MessageContainer sendHostMessage(String uuid, MessageContainer.Message message, String roomId) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();
        log.info("sendHostMessage. message: {}, roomId:{}", response, roomId);

        messagingTemplate.convertAndSend(String.format("/subscribe/system/private/%s", roomId), response);
        return response;
    }

    public void sendPrivateMessage(String uuid, MessageContainer.Message message, String senderId) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();

        messagingTemplate.convertAndSend(String.format("/subscribe/system/private/%s", senderId), response);
    }

    public void sendPublicMessage(String uuid, MessageContainer.Message message, String roomId) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();

        messagingTemplate.convertAndSend(String.format("/subscribe/system/public/%s", roomId), response);
    }
}
