package com.game.liar.repository;

import com.game.liar.dto.Member;
import com.game.liar.dto.Room;
import com.game.liar.dto.request.RoomIdRequest;
import com.game.liar.exception.AlreadyExistException;
import com.game.liar.exception.MaxCountException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Repository
@Slf4j
public class RoomRepository {
    private final Map<String, Room> roomMap = new HashMap<>();

    public int getRoomCount() {
        return roomMap.size();
    }

    public Room create(com.game.liar.dto.request.RoomInfoRequest request) throws MaxCountException, AlreadyExistException {
        Room room = makeRoom(request);
        addRoom(room);
        return room;
    }

    private Room makeRoom(com.game.liar.dto.request.RoomInfoRequest request) throws MaxCountException {
        String roomId;
        while (true) {
            roomId = request.getRoomName();
            if (roomMap.containsKey(roomId)) continue;
            break;
        }
        Room room = new Room();
        room.setRoomId(roomId);
        room.setRoomName(UUID.randomUUID().toString());
        room.setOwnerId(request.getSenderId());
        room.setMaxCount(request.getMaxPersonCount());

        room.addMember(new Member(request.getSenderId()));
        return room;
    }

    private void addRoom(Room room) {
        roomMap.put(room.getRoomId(), room);
    }

    public void clearRooms() {
        roomMap.clear();
    }

    public Room getRoom(RoomIdRequest request) {
        return roomMap.getOrDefault(request.getRoomId(), null);
    }

    public Room addRoomMember(RoomIdRequest request) throws MaxCountException {
        Room room = getRoom(request);
        room.addMember(new Member(request.getSenderId()));
        return room;
    }

    public Room deleteRoom(RoomIdRequest request){

        return roomMap.remove(request.getRoomId());
    }
}
