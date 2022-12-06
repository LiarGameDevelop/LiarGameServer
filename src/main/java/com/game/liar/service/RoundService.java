package com.game.liar.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
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
}
