package com.game.liar.game.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.exception.ErrorResult;
import com.game.liar.exception.JsonDeserializeException;
import com.game.liar.exception.LiarGameException;
import com.game.liar.exception.NotAllowedActionException;
import com.game.liar.game.config.TimerInfoThreadPoolTaskScheduler;
import com.game.liar.game.domain.GameInfo;
import com.game.liar.game.domain.Global;
import com.game.liar.game.dto.MessageContainer;
import com.game.liar.game.dto.request.KeywordRequest;
import com.game.liar.game.dto.request.LiarDesignateRequest;
import com.game.liar.game.dto.response.*;
import com.game.liar.game.service.GameService;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.dto.UserDataDto;
import com.game.liar.room.event.UserAddedEvent;
import com.game.liar.room.event.UserRemovedEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

import static com.game.liar.game.domain.Global.*;

@RestController
@Slf4j
public class GameController {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final RabbitTemplate messagingTemplate;
    private GameService gameService;

    private final TimerInfoThreadPoolTaskScheduler taskScheduler;
    @Setter
    private long timeout = 20000;

    //public GameController(SimpMessagingTemplate messagingTemplate, GameService gameService, TaskScheduler taskScheduler) {
    public GameController(RabbitTemplate messagingTemplate, GameService gameService, TimerInfoThreadPoolTaskScheduler taskScheduler) {
        this.messagingTemplate = messagingTemplate;
        this.gameService = gameService;
        this.taskScheduler = taskScheduler;
    }

    //TODO : refactoring
    @SneakyThrows
    @MessageExceptionHandler
    //@SendToUser(destinations = "/subscribe/errors", broadcast = false)
    public void LiarGameExceptionHandler(LiarGameException ex, String requestStr) {
        log.info("LiarGameExceptionHandler error {} from [request:{}]", ex.getMessage(), requestStr);
        MessageContainer request;
        try {
            request = objectMapper.readValue(requestStr, MessageContainer.class);
        } catch (JsonProcessingException e) {
            //TODO: Find how to send message to user directly
            log.error("Json Parsing error : ex from [request:{}]", requestStr);
//            return MessageContainer.messageContainerBuilder()
//                    .senderId("SERVER")
//                    .message(new MessageContainer.Message(null, new ErrorResponse(objectMapper.writeValueAsString(new ErrorResult(ex.getCode(), ex.getMessage())))))
//                    .build();
            return;
        }
        sendErrorMessage(request.getUuid(), new MessageContainer.Message(
                apiRequestMapper.get(request.getMessage().getMethod()) != null ? apiRequestMapper.get(request.getMessage().getMethod()) : "METHOD_ERROR"
                , new ErrorResponse(objectMapper.writeValueAsString(new ErrorResult(ex.getCode(), ex.getMessage())))), request.getSenderId());
    }

    @MessageMapping("private.{roomId}")
    public void handlePrivateMessage(@Payload String requestStr, @DestinationVariable("roomId") String roomId) throws JsonDeserializeException {
        log.info("[private] message from room id({}) : {}", roomId, requestStr);
        messageHandler(roomId, requestStr);
    }

    private void messageHandler(String roomId, String requestStr) throws JsonDeserializeException {
        if (gameService.checkRoomExist(RoomId.of(roomId))) {
            MessageContainer request;
            try {
                request = objectMapper.readValue(requestStr, MessageContainer.class);
            } catch (JsonProcessingException e) {
                throw new JsonDeserializeException("JSON format doest not fit for JAVA object. Please check reference");
            }
            String method = request.getMessage().getMethod();
            messageMapper.get(method).process(request, roomId);
        } else {
            log.error("mapped room id does not exist, room id : {}", roomId);
        }
    }

    @EventListener
    @Async
    public void onUserAdded(UserAddedEvent event) {
        log.info("[onUserAdded] user Added. event :{}", event);
        String roomId = event.getRoomId();
        UserDataDto user = event.getUser();
        if (roomId == null || user == null)
            throw new IllegalArgumentException("room id/user info should be required");
        gameService.addMember(roomId, user);
    }

    @EventListener
    @Async
    public void onUserRemoved(UserRemovedEvent event) {
        log.info("[onUserRemoved] user Removed. event :{}", event);
        String roomId = event.getRoomId();
        UserDataDto user = event.getUser();
        if (roomId == null || user == null)
            throw new IllegalArgumentException("room id/user info should be required");
        gameService.deleteMember(roomId, user);

        //TODO: user login/logout 한곳에서 관리
        LogoutInfo logoutInfo = new LogoutInfo(roomId, user.getUserId(), false);
        messagingTemplate.convertAndSend("amq.topic", String.format("room.%s.logout", roomId), logoutInfo);
    }

    @AllArgsConstructor
    @Getter
    private static class LogoutInfo {
        String roomId;
        String userId;
        boolean login;
    }

    @FunctionalInterface
    public interface ProcessGame {
        void process(MessageContainer request, String roomId);
    }

    ProcessGame getGameState = (request, roomId) -> {
        GameInfo gameInfo = gameService.getGameState(roomId);
        GameStateResponse body = new GameStateResponse(gameInfo.getState());
        sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_STATE, body), request.getSenderId(), roomId);
    };

    ProcessGame startGame = (request, roomId) -> {
        GameInfo gameInfo = gameService.startGame(request, roomId);
        GameStateResponse body = new GameStateResponse(gameInfo.getState());
        log.info("[API]startGame response : {}, room id: {}", body, roomId);
        sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_STARTED, body), roomId);
    };

    ProcessGame startRound = (request, roomId) -> {
        GameInfo gameInfo = gameService.startRound(request, roomId);
        RoundResponse body = new RoundResponse(gameInfo.getState(), gameInfo.getCurrentRound());
        log.info("[API]startRound response : {}, room id: {}", body, roomId);
        sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_ROUND_STARTED, body), roomId);
    };

    ProcessGame selectLiar = (request, roomId) -> {
        GameInfo gameInfo = gameService.selectLiar(request, roomId);
        for (String userId : gameService.getUserIdListInRoom(roomId)) {
            boolean isLiar = userId.equals(gameInfo.getLiarId());
            LiarResponse body = new LiarResponse(gameInfo.getState(), isLiar);
            log.info("[API]selectLiar response : {} to user : {}", body, userId);
            sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_LIAR_SELECTED, body), userId, roomId);
        }
    };

    ProcessGame openKeyword = (request, roomId) -> {
        GameInfo gameInfo = gameService.openKeyword(request, roomId);
        for (String userId : gameService.getUserIdListInRoom(roomId)) {
            boolean isLiar = userId.equals(gameInfo.getLiarId());
            OpenedGameInfo body = OpenedGameInfo.builder()
                    .category(gameInfo.getCurrentRoundCategory())
                    .keyword(isLiar ? "" : gameInfo.getCurrentRoundKeyword())
                    .turnOrder(gameInfo.getTurnOrder())
                    .build();
            log.info("[API]openKeyword response : {}", body);
            sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_KEYWORD_OPENED, body), userId, roomId);
        }
        //Notify first user turn after keyword opened
        log.info("[API]__requestTurnFinished call from server. [room:{}]", roomId);

        __requestTurnFinished(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
    };

    ProcessGame requestTurnFinished = this::__requestTurnFinished;

    private void __requestTurnFinished(MessageContainer request, String roomId) {
        log.info("[API]requestTurnFinished request : {}", request);
        String senderId = request.getSenderId();
        GameInfo gameInfo = gameService.updateTurn(senderId, roomId);
        String clientId = gameService.getCurrentTurnUser(roomId);
        gameService.cancelTurnTimer(roomId);
        taskScheduler.cancel(new TimerInfoThreadPoolTaskScheduler.TimerInfo(roomId));

        try {
            if (gameService.isLastTurn(roomId)) {
                __notifyFindingLiarEnd(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
                return;
            } else {
                TurnResponse body = new TurnResponse(gameInfo.getState(), clientId);
                log.info("[API]requestTurnFinished response : {}", body);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_TURN, body), roomId);
                __registerTurnTimeoutNotification(request, roomId);
            }
        } catch (NotAllowedActionException e) {
            //모든 turn이 끝났으므로 다음 state로 바꿔야함.
            log.info("[API]requestTurnFinished response exception: The round is over, change to next state");
        }
        if (gameService.isLastTurn(roomId)) {
            __notifyFindingLiarEnd(new MessageContainer(SERVER_ID, null, UUID.randomUUID().toString()), roomId);
        }
    }

    ProcessGame voteLiar = this::__voteLiar;

    private void __voteLiar(MessageContainer request, String roomId) {
        gameService.voteLiar(request, roomId);
        if (gameService.isVoteFinished(roomId)) {
            gameService.cancelVoteTimer(roomId);
            taskScheduler.cancel(new TimerInfoThreadPoolTaskScheduler.TimerInfo(roomId));
            VoteResult voteResult = gameService.getMostVoted(roomId);
            log.info("[API]voteLiar response : {}", voteResult);
            String uuid = UUID.randomUUID().toString();
            sendPublicMessage(uuid, new MessageContainer.Message(NOTIFY_VOTE_RESULT, voteResult), roomId);

            if (voteResult.getMostVoted() != null) {
                if (voteResult.getMostVoted().size() == 1) {
                    GameInfo gameInfo = gameService.nextGameState(roomId);
                    sendPrivateMessage(
                            uuid
                            , new MessageContainer.Message(NOTIFY_LIAR_OPEN_REQUEST, new GameStateResponse(gameInfo.getState()))
                            , gameInfo.getOwnerId().getUserId()
                            , roomId);
                } else {
                    sendNeedVote(roomId);
                }
            } else {
                sendNeedVote(roomId);
            }
        }
    }

    private void sendNeedVote(String roomId) {
        String uuid = UUID.randomUUID().toString();
        GameInfo gameInfo = gameService.resetVoteResult(roomId);
        GameStateResponse body = new GameStateResponse(gameInfo.getState());
        sendPublicMessage(uuid, new MessageContainer.Message(NOTIFY_NEW_VOTE_NEEDED, body), roomId);
        __registerVoteTimeoutNotification(new MessageContainer("SERVER", null, uuid), roomId);
    }

    ProcessGame openLiar = (request, roomId) -> {
        OpenLiarResponse body = gameService.openLiar(request, roomId);
        log.info("[API]openLiar response : {}", body);
        sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_OPENED, body), roomId);

        __notifyLiarAnswerNeeded(roomId);
        __registerLiarAnswerTimeoutNotification(request, roomId);
    };

    ProcessGame checkKeywordCorrect = this::__checkKeywordCorrect;

    private void __checkKeywordCorrect(MessageContainer request, String roomId) {
        LiarAnswerResponse body = gameService.checkKeywordCorrect(request, roomId);
        gameService.cancelAnswerTimer(roomId);
        taskScheduler.cancel(new TimerInfoThreadPoolTaskScheduler.TimerInfo(roomId));
        log.info("[API]checkKeywordCorrect response : {}", body);
        sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_CORRECT, body), roomId);
    }


    ProcessGame openScores = (request, roomId) -> {
        ScoreboardResponse body = gameService.notifyScores(request, roomId);
        log.info("[API]notifyScores response : {}", body);
        sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_SCORES, body), roomId);
        __notifyRoundEnd(request, roomId);
    };

    ProcessGame publishRankings = (request, roomId) -> {
        RankingsResponse body = gameService.publishRankings(request, roomId);
        log.info("[API]publishRankings response : {}", body);
        sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_RANKINGS_PUBLISHED, body), roomId);
        __notifyGameEnd(roomId);
    };

    ProcessGame getGameCategory = (request, roomId) -> {
        GameCategoryResponse body = gameService.getGameCategory(roomId);
        log.info("[API]getGameCategory response : {}", body);
        sendPrivateMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_GAME_CATEGORY, body), request.getSenderId(), roomId);
    };

    private void __notifyLiarAnswerNeeded(String roomId) {
        GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
        String liar = gameInfo.getLiarId();
        log.info("[API]notifyLiarAnswerNeeded from [room:{}] to [liar:{}]", roomId, liar);
        sendPrivateMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_NEEDED, null), liar, roomId);
    }

    private void __notifyFindingLiarEnd(MessageContainer request, String roomId) {
        GameInfo gameInfo = gameService.notifyFindingLiarEnd(roomId);
        log.info("[API]notifyFindingLiarEnd from [room:{}]", roomId);
        RoundResponse body = new RoundResponse(gameInfo.getState(), gameInfo.getCurrentRound());
        sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_FINDING_LIAR_END, body), roomId);
        __registerVoteTimeoutNotification(request, roomId);
    }


    private void __notifyRoundEnd(MessageContainer request, String roomId) {
        GameInfo gameInfo = gameService.notifyRoundEnd(roomId);
        log.info("[API]notifyRoundEnd from [room:{}]", roomId);
        RoundResponse body = new RoundResponse(gameInfo.getState(), gameInfo.getCurrentRound());
        sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_ROUND_END, body), roomId);
        gameService.resetVoteResult(roomId);
    }

    private void __notifyGameEnd(String roomId) {
        log.info("[API]notifyGameEnd from [room:{}]", roomId);
        GameInfo gameInfo = gameService.nextGameState(roomId);
        GameStateResponse body = new GameStateResponse(gameInfo.getState());
        sendPublicMessage(UUID.randomUUID().toString(), new MessageContainer.Message(NOTIFY_GAME_END, body), roomId);
        gameService.resetGame(roomId);
    }

    private final Map<String, ProcessGame> messageMapper = new HashMap<String, ProcessGame>() {
        {
            put(Global.START_GAME, startGame);
            put(Global.START_ROUND, startRound);
            put(Global.SELECT_LIAR, selectLiar);
            put(Global.OPEN_KEYWORD, openKeyword);
            put(Global.VOTE_LIAR, voteLiar);
            put(Global.OPEN_LIAR, openLiar);
            put(Global.CHECK_KEYWORD_CORRECT, checkKeywordCorrect);
            put(Global.OPEN_SCORES, openScores);
            put(Global.PUBLISH_RANKINGS, publishRankings);
            put(Global.GET_GATE_STATE, getGameState);
            put(Global.REQUEST_TURN_FINISH, requestTurnFinished);
            put(Global.GET_GAME_CATEGORY, getGameCategory);
        }
    };

    @Transactional
    protected void __registerTurnTimeoutNotification(MessageContainer request, String roomId) {
        log.info("[API]register notifyTurnTimeout from [room:{}]", roomId);
        taskScheduler.schedule(new TimerTask() {
            @Override
            public void run() {
                GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
                log.info("[API]timer run notifyTurnTimeout from [room:{}][timer:{}]", roomId, this);
                if (!gameInfo.isTurnTimerRunning()) {
                    this.cancel();
                    return;
                }
                log.info("[API]notifyTurnTimeout from [room:{}]", roomId);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_TURN_TIMEOUT, null), roomId);
                //타임아웃났다고 알리고, 다음 턴의 사람으로 바꿔야함. 다음턴의 사람이 보냈다고 해야함.
                __requestTurnFinished(new MessageContainer(gameInfo.getCurrentTurnId(), null, UUID.randomUUID().toString()), roomId);
            }
        }, Instant.now().plusMillis(timeout), new TimerInfoThreadPoolTaskScheduler.TimerInfo(roomId));
        gameService.startTurnTimer(roomId);
    }

    private void __registerVoteTimeoutNotification(MessageContainer request, String roomId) {
        log.info("[API]register notifyVoteTimeout from [room:{}]", roomId);
        taskScheduler.schedule(new TimerTask() {
            @Override
            public void run() {
                GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
                if (!gameInfo.isVoteTimerRunning()) return;
                log.info("[API]notifyVoteTimeout from [room:{}]", roomId);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_VOTE_TIMEOUT, null), roomId);
                //타임아웃났다고 알리고, 투표완료시켜야함
                List<String> notVoteUserList = gameService.getNotVoteUserList(roomId);
                log.info("[API]notifyVoteTimeout notVote user :{}", notVoteUserList);
                for (String userId : notVoteUserList) {
                    __voteLiar(new MessageContainer(userId,
                            new MessageContainer.Message(VOTE_LIAR,
                                    new LiarDesignateRequest("")),
                            UUID.randomUUID().toString()), roomId);
                }
            }
        }, Instant.now().plusMillis(timeout), new TimerInfoThreadPoolTaskScheduler.TimerInfo(roomId));
        gameService.startVoteTimer(roomId);
    }

    private void __registerLiarAnswerTimeoutNotification(MessageContainer request, String roomId) {
        log.info("[API]register notifyAnswerTimeout from [room:{}]", roomId);
        taskScheduler.schedule(new TimerTask() {
            @Override
            public void run() {
                GameInfo gameInfo = gameService.getGame(RoomId.of(roomId));
                if (!gameInfo.isAnswerTimerRunning()) return;
                log.info("[API]notifyLiarAnswerTimeout from [room:{}]", roomId);
                sendPublicMessage(request.getUuid(), new MessageContainer.Message(NOTIFY_LIAR_ANSWER_TIMEOUT, null), roomId);
                //타임아웃났다고 알리고, checkKeywordCorrect 요청
                __checkKeywordCorrect(new MessageContainer(gameInfo.getLiarId(),
                                new MessageContainer.Message(CHECK_KEYWORD_CORRECT,
                                        new KeywordRequest("")),
                                UUID.randomUUID().toString()),
                        roomId);
            }
        }, Instant.now().plusMillis(timeout), new TimerInfoThreadPoolTaskScheduler.TimerInfo(roomId));
        gameService.startAnswerTimer(roomId);
    }


    //TODO: introduce message service
    public void sendPrivateMessage(String uuid, MessageContainer.Message message, String receiver, String roomId) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();
        log.info("Send private message. message: {}, [receiver:{}]", response, receiver);
        messagingTemplate.convertAndSend("message.direct", String.format("room.%s.user.%s", roomId, receiver), response); //queue에 직접
    }

    public void sendPublicMessage(String uuid, MessageContainer.Message message, String roomId) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();
        log.info("Send public message. message: {}, [room:{}]", response, roomId);
        messagingTemplate.convertAndSend("amq.topic", String.format("room.%s.user.*", roomId), response);
    }

    public void sendErrorMessage(String uuid, MessageContainer.Message message, String receiver) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();
        log.info("Send error message. message: {}, [receiver:{}]", response, receiver);
        messagingTemplate.convertAndSend("message.error", String.format("user.%s", receiver), response);
    }
}