package com.game.liar.service;

import com.game.liar.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RoundService {

    public String selectLiar(List<String> usersInRoom, String roomId) {
        Random random = new Random(); // 랜덤 객체 생성
        random.setSeed(System.currentTimeMillis() + UUID.fromString(roomId).hashCode());
        int index = random.nextInt(usersInRoom.size());

        return usersInRoom.get(index);
    }

    public List<String> makeTurnOrder(List<String> usersInRoom) {
        return null;
    }

    public void openCategory(GameInfo gameInfo) {
        List<String> valuesList = new ArrayList<>(gameInfo.getSelectedByRoomOwnerCategory().keySet());
        Collections.shuffle(valuesList);
        gameInfo.setCurrentRoundCategory(valuesList.get(0));
        log.info("current round category : {}", gameInfo.getCurrentRoundCategory());
    }

    public void openKeyword(GameInfo gameInfo) {
        List<String> valuesList = new ArrayList<>(gameInfo.getSelectedByRoomOwnerCategory().get(gameInfo.getCurrentRoundCategory()));
        Collections.shuffle(valuesList);
        gameInfo.setCurrentRoundKeyword(valuesList.get(0));
        log.info("current round keyword : {}", gameInfo.getCurrentRoundKeyword());
    }

    public void openTurnOrder(GameInfo gameInfo) {
        List<String> valuesList = gameInfo.getUserList().stream().map(User::getUserId).collect(Collectors.toList());
        Collections.shuffle(valuesList);
        gameInfo.setTurnOrder(valuesList);
        log.info("current round turn order : {}", gameInfo.getTurnOrder());
    }
}
