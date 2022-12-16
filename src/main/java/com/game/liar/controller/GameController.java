package com.game.liar.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.domain.GameState;
import com.game.liar.domain.Global;
import com.game.liar.domain.User;
import com.game.liar.domain.request.MessageContainer;
import com.game.liar.domain.response.*;
import com.game.liar.exception.NotAllowedActionException;
import com.game.liar.service.GameInfo;
import com.game.liar.service.GameService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;
import java.util.UUID;

import static com.game.liar.domain.Global.*;

@RestController
@Slf4j
public class GameController {

    private static ObjectMapper objectMapper = new ObjectMapper();

    private SimpMessagingTemplate messagingTemplate;
    private GameService gameService;
    @Setter
    private long timeout = 20000;

    public GameController(SimpMessagingTemplate messagingTemplate, GameService gameService) {
        this.messagingTemplate = messagingTemplate;
        this.gameService = gameService;
    }

    //TODO: use validation
    @MessageMapping("/system/private/{roomId}")
    public void sendHostMessage(@Payload MessageContainer request, @DestinationVariable("roomId") String roomId) {
        log.info("[private] message from room id({}) : {}", roomId, request);
        if (gameService.checkRoomExist(roomId)) {
            String method = request.getMessage().getMethod();
            processMapper.get(method).process(request, roomId);
        } else {
            log.error("mapped room id does not exist, room id : {}", roomId);
        }
    }

    @MessageMapping("/system/public/{roomId}")
    public void sendPublicMessage(@Payload MessageContainer request, @DestinationVariable("roomId") String roomId) {
        log.info("[public] message from room id({}) : {}", roomId, request);
        if (gameService.checkRoomExist(roomId)) {
            String method = request.getMessage().getMethod();
            processMapper.get(method).process(request, roomId);
        }
    }

    public void addRoom(String roomName, String ownerId) {
        gameService.addGame(roomName, ownerId);
    }

    public void addMember(String roomId, User userList) {
        gameService.addMember(roomId, userList);
    }

    public void deleteMember(String roomId, User userList) {
        gameService.deleteMember(roomId, userList);
    }

    @FunctionalInterface
    public interface ProcessGame {
        void process(MessageContainer request, String roomId);
    }

    ProcessGame getGameState = (request, roomId) -> {
        GameInfo gameInfo = gameService.getGameState(roomId);
        try {
            GameState state = gameInfo.getState();
            sendHostMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_STATE, objectMapper.writeValueAsString(new GameStateResponse(gameInfo.getState()))), roomId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    };

    ProcessGame startGame = (request, roomId) -> {
        GameInfo gameInfo = gameService.startGame(request, roomId);
        try {
            sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_STARTED, objectMapper.writeValueAsString(new GameStateResponse(gameInfo.getState()))), roomId);
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
        for (String userId : gameService.getUserIDsInRoom(roomId)) {
            boolean isLiar = userId.equals(gameInfo.getLiarId());
            try {
                body = objectMapper.writeValueAsString(new LiarResponse(isLiar, gameInfo.getState()));
                log.info("[API]selectLiar response : {}", body);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_LIAR_SELECTED, body), userId);
        }
    };

    ProcessGame openKeyword = (request, roomId) -> {
        GameInfo gameInfo = gameService.openKeyword(request, roomId);
        String body = "";
        for (String userId : gameService.getUserIDsInRoom(roomId)) {
            boolean isLiar = userId.equals(gameInfo.getLiarId());
            OpenedGameInfo response = OpenedGameInfo.builder()
                    .category(gameInfo.getCurrentRoundCategory())
                    .keyword(isLiar ? "LIAR" : gameInfo.getCurrentRoundKeyword())
                    .turnOrder(gameInfo.getTurnOrder())
                    .state(gameInfo.getState())
                    .build();
            try {
                body = objectMapper.writeValueAsString(response);
                log.info("[API]openKeyword response : {}", body);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_KEYWORD_OPENED, body), userId);
        }
        //Notify first user turn after keyword opened
        __requestTurnFinished(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
    };

    ProcessGame requestTurnFinished = this::__requestTurnFinished;

    private void __requestTurnFinished(MessageContainer request, String roomId) {
        String senderId = request.getSenderId();
        GameInfo gameInfo = gameService.updateTurn(senderId, roomId);
        String clientId = gameInfo.getCurrentTurnId();
        gameInfo.cancelTimer();

        try {
            if (gameInfo.isLastTurn()) {
                __notifyRoundEnd(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
                return;
            }
            else {
                String body = objectMapper.writeValueAsString(new TurnResponse(clientId, gameInfo.getState()));
                log.info("[API]requestTurnFinished response : {}", body);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_TURN, body), roomId);
                resetTask(request, roomId, gameInfo);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (NotAllowedActionException e) {
            //모든 turn이 끝났으므로 다음 state로 바꿔야함.
            log.info("[API]requestTurnFinished response exception: The round is over, change to next state");
        }
        if (gameInfo.isLastTurn()) {
            __notifyRoundEnd(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
        }
    }

    private void resetTask(MessageContainer request, String roomId, GameInfo gameInfo) {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                log.info("[API]notifyTurnTimeout from room id :{}", roomId);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_TURN_TIMEOUT, "{}"), roomId);
                //타임아웃났다고 알리고, 다음 턴의 사람으로 바꿔야함. 다음턴의 사람이 보냈다고 해야함.
                __requestTurnFinished(new MessageContainer(gameInfo.getCurrentTurnId(), null, UUID.randomUUID().toString()), roomId);
            }
        };
        gameInfo.scheduleTimer(task, timeout);
    }

    private void __notifyRoundEnd(MessageContainer request, String roomId) {
        GameInfo gameInfo = gameService.notifyRoundEnd(roomId);

        try {
            log.info("[API]notifyRoundEnd from room id :{}", roomId);
            String body = objectMapper.writeValueAsString(new RoundInfoResponse(gameInfo.getState(), gameInfo.getRound()));
            sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_ROUND_END, body), roomId);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private final Map<String, ProcessGame> processMapper = new HashMap<String, ProcessGame>() {
        {
            put(Global.GET_GATE_STATE, getGameState);
            put(Global.START_GAME, startGame);
            put(Global.START_ROUND, startRound);
            put(Global.SELECT_LIAR, selectLiar);
            put(Global.OPEN_KEYWORD, openKeyword);
            put(Global.REQUEST_TURN_FINISH, requestTurnFinished);
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

        log.info("Send Private message : {}", response);
        messagingTemplate.convertAndSend(String.format("/subscribe/system/private/%s", senderId), response);
    }

    public void sendPublicMessage(String uuid, MessageContainer.Message message, String roomId) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();

        log.info("Send Public message : {}", response);
        messagingTemplate.convertAndSend(String.format("/subscribe/system/public/%s", roomId), response);
    }
}
