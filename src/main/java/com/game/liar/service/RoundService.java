package com.game.liar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.domain.Global;
import com.game.liar.domain.request.MessageRequest;
import com.game.liar.exception.NotAllowedActionException;
import com.game.liar.exception.NotExistException;
import org.springframework.stereotype.Service;

@Service
public class GameDetailService {
    ObjectMapper objectMapper = new ObjectMapper();

    public void startGame(MessageRequest.Message message, GameInfo gameInfo) {
        String method = message.getMethod();
        if (!method.equals(Global.START_GAME)) {
            throw new NotExistException("Request Method is not start game");
        }
        try {
            GameInfo.GameSettings detail = objectMapper.readValue(message.getBody(), GameInfo.GameSettings.class);
            if (detail.getRound() == null || detail.getTurn() == null || detail.getCategory() == null) {
                throw new NullPointerException("Required parameters does exist");
            }
            if (detail.getRound() <= 0 || detail.getRound() >= 6) {
                throw new NotAllowedActionException("Round can be 1 to 5");
            }
            if(detail.getTurn() <= 0 || detail.getTurn() >= 4){
                throw new NotAllowedActionException("Turn can be 1 to 3");
            }
            if( detail.getCategory().isEmpty()){
                throw new NotAllowedActionException("Category should contain more than 1");
            }
            gameInfo.setGameSettings(detail);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
