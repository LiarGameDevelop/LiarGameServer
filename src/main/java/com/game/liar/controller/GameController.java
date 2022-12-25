package com.game.liar.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.domain.Global;
import com.game.liar.domain.User;
import com.game.liar.dto.request.KeywordRequest;
import com.game.liar.dto.request.LiarDesignateRequest;
import com.game.liar.dto.request.MessageContainer;
import com.game.liar.dto.response.*;
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

import java.util.*;

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
    public void sendPrivateMessage(@Payload MessageContainer request, @DestinationVariable("roomId") String roomId) {
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
            String body = objectMapper.writeValueAsString(new GameStateResponse(gameInfo.getState()));
            sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_STATE, body), request.getSenderId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    };

    ProcessGame startGame = (request, roomId) -> {
        GameInfo gameInfo = gameService.startGame(request, roomId);
        try {
            String body = objectMapper.writeValueAsString(new GameStateResponse(gameInfo.getState()));
            sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_STARTED, body), roomId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    };

    ProcessGame startRound = (request, roomId) -> {
        GameInfo gameInfo = gameService.startRound(request, roomId);
        try {
            String body = objectMapper.writeValueAsString(new RoundInfoResponse(gameInfo.getState(), gameInfo.getRound()));
            log.info("[API]startRound response : {}, room id: {}", body, roomId);
            sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_ROUND_STARTED, body), roomId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    };

    ProcessGame selectLiar = (request, roomId) -> {
        GameInfo gameInfo = gameService.selectLiar(request, roomId);
        String body = "";
        for (String userId : gameService.getUserIdListInRoom(roomId)) {
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
        for (String userId : gameService.getUserIdListInRoom(roomId)) {
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
        log.info("[API]__requestTurnFinished call from server. room id : {}", roomId);
        __requestTurnFinished(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
    };

    ProcessGame requestTurnFinished = this::__requestTurnFinished;

    private void __requestTurnFinished(MessageContainer request, String roomId) {
        String senderId = request.getSenderId();
        GameInfo gameInfo = gameService.updateTurn(senderId, roomId);
        String clientId = gameInfo.getCurrentTurnId();
        gameService.cancelTurnTimer(roomId);

        try {
            if (gameInfo.isLastTurn()) {
                __notifyFindingLiarEnd(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
                return;
            } else {
                String body = objectMapper.writeValueAsString(new TurnResponse(clientId, gameInfo.getState()));
                log.info("[API]requestTurnFinished response : {}", body);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_TURN, body), roomId);
                __registerTurnTimeoutNotification(request, roomId, gameInfo);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (NotAllowedActionException e) {
            //모든 turn이 끝났으므로 다음 state로 바꿔야함.
            log.info("[API]requestTurnFinished response exception: The round is over, change to next state");
        }
        if (gameInfo.isLastTurn()) {
            __notifyFindingLiarEnd(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
        }
    }

    ProcessGame voteLiar = (request, roomId) -> {
        __voteLiar(request, roomId);
    };

    private void __voteLiar(MessageContainer request, String roomId) {
        GameInfo gameInfo = gameService.voteLiar(request, roomId);
        if (gameInfo.voteFinished()) {
            gameService.cancelVoteTimer(roomId);
            String body;
            try {
                VoteResult voteResult = gameService.getMostVoted(roomId);
                body = objectMapper.writeValueAsString(voteResult);
                log.info("[API]voteLiar response : {}", body);
                String uuid = UUID.randomUUID().toString();
                sendPublicMessage(uuid, new MessageContainer.Message(NOTIFY_VOTE_RESULT, body), roomId);

                if (voteResult.getMostVoted().size() == 1) {
                    gameInfo.nextState();
                    uuid = UUID.randomUUID().toString();
                    body = objectMapper.writeValueAsString(new GameStateResponse(gameInfo.getState()));
                    sendPublicMessage(uuid, new MessageContainer.Message(NOTIFY_LIAR_OPENED, body), roomId);
                } else {
                    uuid = UUID.randomUUID().toString();
                    body = objectMapper.writeValueAsString(new GameStateResponse(gameInfo.getState()));
                    gameInfo.resetVoteResult();

                    sendPublicMessage(uuid, new MessageContainer.Message(NOTIFY_NEW_VOTE_NEEDED, body), roomId);
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    ProcessGame openLiar = (request, roomId) -> {
        OpenLiarResponse openLiarResponse = gameService.openLiar(request, roomId);
        String body;
        try {
            body = objectMapper.writeValueAsString(openLiarResponse);
            log.info("[API]openLiar response : {}", body);
            sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_OPENED, body), roomId);

            __notifyLiarAnswerNeeded(roomId);
            __registerLiarAnswerTimeoutNotification(request, roomId, gameService.getGame(roomId));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    };

    ProcessGame checkKeywordCorrect = this::__checkKeywordCorrect;

    private void __checkKeywordCorrect(MessageContainer request, String roomId) {
        gameService.cancelAnswerTimer(roomId);
        LiarAnswerResponse liarAnswerResponse = gameService.checkKeywordCorrect(request, roomId);
        String body;
        try {
            body = objectMapper.writeValueAsString(liarAnswerResponse);
            log.info("[API]checkKeywordCorrect response : {}", body);
            sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_CORRECT, body), roomId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    ProcessGame openScores = (request, roomId) -> {
        ScoreBoardResponse scoreBoardResponse = gameService.notifyScores(request, roomId);
        String body;
        try {
            body = objectMapper.writeValueAsString(scoreBoardResponse);
            log.info("[API]notifyScores response : {}", body);
            sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_SCORES, body), roomId);
            __notifyRoundEnd(request, roomId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    };

    ProcessGame publishRankings = (request, roomId) -> {
        Rankings rankings = gameService.publishRankings(request, roomId);
        String body;
        try {
            body = objectMapper.writeValueAsString(rankings);
            log.info("[API]publishRankings response : {}", body);
            sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_RANKINGS_PUBLISHED, body), roomId);
            __notifyGameEnd(roomId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    };

    private void __notifyLiarAnswerNeeded(String roomId) {
        GameInfo gameInfo = gameService.getGame(roomId);
        String liar = gameInfo.getLiarId();
        log.info("[API]notifyLiarAnswerNeeded from room id :{}", roomId);
        sendPrivateMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_NEEDED, "{}"), liar);
    }

    private void __notifyFindingLiarEnd(MessageContainer request, String roomId) {
        GameInfo gameInfo = gameService.notifyFindingLiarEnd(roomId);

        try {
            log.info("[API]notifyFindingLiarEnd from [room:{}]", roomId);
            String body = objectMapper.writeValueAsString(new RoundInfoResponse(gameInfo.getState(), gameInfo.getRound()));
            sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_FINDING_LIAR_END, body), roomId);
            __registerVoteTimeoutNotification(request, roomId, gameInfo);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void __notifyRoundEnd(MessageContainer request, String roomId) {
        GameInfo gameInfo = gameService.notifyRoundEnd(roomId);
        try {
            log.info("[API]notifyRoundEnd from [room:{}]", roomId);
            String body = objectMapper.writeValueAsString(new RoundInfoResponse(gameInfo.getState(), gameInfo.getRound()));
            sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_ROUND_END, body), roomId);
            gameInfo.resetVoteResult();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void __notifyGameEnd(String roomId) {
        GameInfo gameInfo = gameService.getGame(roomId);
        log.info("[API]notifyGameEnd from room id :{}", roomId);
        gameInfo.nextState();
        try {
            String body = objectMapper.writeValueAsString(new GameStateResponse(gameInfo.getState()));
            sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_GAME_END, body), roomId);
            gameService.resetGame(roomId);
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
            put(Global.VOTE_LIAR, voteLiar);
            put(Global.OPEN_LIAR, openLiar);
            put(Global.CHECK_KEYWORD_CORRECT, checkKeywordCorrect);
            put(Global.OPEN_SCORES, openScores);
            put(Global.PUBLISH_RANKINGS, publishRankings);
        }
    };

    private void __registerTurnTimeoutNotification(MessageContainer request, String roomId, GameInfo gameInfo) {
        log.info("[API]register notifyTurnTimeout from room id :{}", roomId);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                log.info("[API]notifyTurnTimeout from room id :{}", roomId);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_TURN_TIMEOUT, "{}"), roomId);
                //타임아웃났다고 알리고, 다음 턴의 사람으로 바꿔야함. 다음턴의 사람이 보냈다고 해야함.
                __requestTurnFinished(new MessageContainer(gameInfo.getCurrentTurnId(), null, UUID.randomUUID().toString()), roomId);
            }
        };
        gameInfo.scheduleTurnTimer(task, timeout);
    }

    private void __registerVoteTimeoutNotification(MessageContainer request, String roomId, GameInfo gameInfo) {
        log.info("[API]register notifyVoteTimeout from room id :{}", roomId);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                log.info("[API]notifyVoteTimeout from room id :{}", roomId);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_VOTE_TIMEOUT, "{}"), roomId);
                //타임아웃났다고 알리고, 투표완료시켜야함
                List<String> notVoteUserList = gameService.getNotVoteUserList(roomId);
                for (String userId : notVoteUserList) {
                    try {
                        __voteLiar(new MessageContainer(userId,
                                        new MessageContainer.Message(VOTE_LIAR,
                                                objectMapper.writeValueAsString(new LiarDesignateRequest(""))),
                                        UUID.randomUUID().toString()), roomId);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
        gameInfo.scheduleVoteTimer(task, timeout);
    }

    private void __registerLiarAnswerTimeoutNotification(MessageContainer request, String roomId, GameInfo gameInfo) {
        log.info("[API]register notifyAnswerTimeout from room id :{}", roomId);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                log.info("[API]notifyLiarAnswerTimeout from room id :{}", roomId);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_TIMEOUT, "{}"), roomId);
                //타임아웃났다고 알리고, checkKeywordCorrect 요청
                try {
                    __checkKeywordCorrect(new MessageContainer(gameInfo.getLiarId(),
                                    new MessageContainer.Message(CHECK_KEYWORD_CORRECT,
                                            objectMapper.writeValueAsString(new KeywordRequest(""))),
                                    UUID.randomUUID().toString()),
                            roomId);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        gameInfo.scheduleAnswerTimer(task, timeout);
    }

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
