package com.game.liar.controller;

import com.game.liar.dto.request.MessageRequest;
import com.game.liar.dto.response.MessageResponse;
import com.game.liar.exception.NotExistException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@RestController
@Slf4j
public class GameController {
    private Map<String, GameObject> gameObjectMap = new ConcurrentHashMap<>();
    private RoomController roomController;

    public GameController(RoomController roomController) {
        this.roomController = roomController;
    }

    @MessageMapping("/system/room/")
    public void sendMessageFromGameManager(@Valid MessageRequest request) {
        //TODO : apply observer pattern
        if(gameObjectMap.containsKey(request.getSenderId())) {
            GameObject gameManager = gameObjectMap.get(request.getSenderId());
            MessageRequest.MessageDetail message = request.getMessage();
            switch (message.getMethod()) {
                case "changeStatus": {
                    gameManager.changeStatus();
                    gameManager.sendMessage(new MessageResponse.MessageDetail(gameManager.getStatus().toString()));
                    break;
                }
                case "getStatus": {
                    gameManager.sendMessage(new MessageResponse.MessageDetail(gameManager.getStatus().toString()));
                    break;
                }
                default: {
                    log.error("There is no method in game manager: {}", message.getMethod());
                    throw new NotExistException("There is no method in game manager");
                }

            }
        }
    }

    protected void initialize(@NotNull String roomName) {

        if (gameObjectMap.containsKey(roomName)) {
            log.error("The game manager already exists");
            return;
        }
        GameObject obj = new GameObject();
        try {
            obj.initialize(roomName);
            gameObjectMap.put(roomName, obj);
            log.debug("game manager created");
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }

    }

    protected void close(@NotNull String roomName) {
        if (!gameObjectMap.containsKey(roomName)) {
            log.error("The game manager does not exists");
            return;
        }
        gameObjectMap.remove(roomName);
        log.debug("game manager destroyed");
    }
}
