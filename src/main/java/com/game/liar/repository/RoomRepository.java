package com.game.liar.repository;

import com.game.liar.dto.Member;
import com.game.liar.dto.Room;
import com.game.liar.dto.request.RoomIdAndSenderIdRequest;
import com.game.liar.dto.request.RoomIdRequest;
import com.game.liar.dto.request.RoomInfoRequest;
import com.game.liar.exception.AlreadyExistException;
import com.game.liar.exception.MaxCountException;
import com.game.liar.exception.NotAllowedActionException;
import com.game.liar.exception.NotExistException;
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

    public Room create(RoomInfoRequest request) throws MaxCountException, AlreadyExistException {
        Room room = makeRoom(request);
        addRoom(room);
        return room;
    }

    private Room makeRoom(RoomInfoRequest request) throws MaxCountException {
        String roomId;
        while (true) {
            roomId = UUID.randomUUID().toString();
            if (roomMap.containsKey(roomId)) continue;
            break;
        }
        Room room = new Room();
        room.setRoomId(UUID.randomUUID().toString());
        room.setRoomName(request.getRoomName());
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

    public Room getRoom(String roomId) {
        return roomMap.getOrDefault(roomId, null);
    }

    public Room addRoomMember(RoomIdAndSenderIdRequest request) throws MaxCountException {
        Room room = getRoom(request.getRoomId());
        if (room == null)
            throw new NotExistException("Requested Room does not exist");
        room.addMember(new Member(request.getSenderId()));
        return room;
    }

    public Room leaveRoomMember(RoomIdAndSenderIdRequest request){
        Room room = getRoom(request.getRoomId());
        if (room == null)
            throw new NotExistException("Requested Room does not exist");
        room.leaveMember(new Member(request.getSenderId()));
        return room;
    }

    public Room deleteRoom(RoomIdAndSenderIdRequest request) {
        if (roomMap.containsKey(request.getRoomId())) {
            String roomOwnerId = roomMap.get(request.getRoomId()).getOwnerId();
            if (request.getSenderId().equals(roomOwnerId)) {
                return roomMap.remove(request.getRoomId());
            }
            throw new NotAllowedActionException("Room owner can delete room");
        }
        throw new NullPointerException();
    }
}
