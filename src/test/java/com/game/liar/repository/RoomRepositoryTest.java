package com.game.liar.repository;

import com.game.liar.game.domain.RoomSettings;
import com.game.liar.room.domain.Room;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.domain.UserId;
import com.game.liar.room.dto.RoomInfoRequest;
import com.game.liar.room.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoomRepositoryTest {
    @Autowired
    private RoomRepository roomRepository;

    @Test
    public void 방만들기_성공() throws Exception {
        //Given
        RoomInfoRequest info = new RoomInfoRequest();
        info.setOwnerName("room1");
        info.setMaxPersonCount(5);

        Room room = new Room(new RoomId("room1"), new RoomSettings(6), new UserId("user1"));

        //When
        Room result = roomRepository.save(room);
        //Then
        assertThat(roomRepository.findAll().size()).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).get()).isEqualTo(result);
    }

    @Test
    @DisplayName("새 룸번호를 생성한다")
    public void generateNewRoomId() throws Exception {
        //Given
        //When
        RoomId roomId = roomRepository.nextRoomId();

        //Then
        assertThat(roomId.getId().length()).isEqualTo(UUID.randomUUID().toString().length());
        assertThat(roomId.getId().matches("(\\w){8}-(\\w){4}-(\\w){4}-(\\w){4}-(\\w){12}")).isTrue();
    }
}
