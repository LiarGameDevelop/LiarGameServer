package com.game.liar.room.repository;

import com.game.liar.room.domain.Room;
import com.game.liar.room.domain.RoomId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface RoomRepository extends JpaRepository<Room,RoomId> {
    Optional<Room> findById(RoomId id);
    default RoomId nextRoomId(){
        return new RoomId(UUID.randomUUID().toString());
    }
}
