package com.game.liar.controller;

import com.game.liar.dto.request.RoomInfoRequest;
import com.game.liar.repository.RoomRepository;
import com.game.liar.service.RoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
class RoomControllerTest {
    private RoomRepository roomRepository = Mockito.mock(RoomRepository.class);
    private RoomService roomService = Mockito.mock(RoomService.class);


    //뭘 테스트하고싶은거니?
    //RoomController를 만들어야한다.
    @Test
    public void 방만들기테스트() throws Exception{
        //Given
        RoomInfoRequest roomInfo= new RoomInfoRequest();
        roomInfo.setMaxPersonCount(5);
        roomInfo.setRoomName("room1");
        roomInfo.setSenderId(UUID.randomUUID().toString());
        RoomController roomController = new RoomController(roomService);

        //Stubbing
        when(roomRepository.getRoomCount()).thenReturn(1);

        //when
        //RoomInfoResponse roomInfoResponse = roomController.create(roomInfo);

        //Then
        assertThat(roomRepository.getRoomCount()).isEqualTo(1);
//        assertThat(roomInfoResponse.getMaxPersonCount()).isEqualTo(3);
//        assertThat(roomInfoResponse.getRoomId()).isEqualTo("123nfj23");
//        assertThat(roomInfoResponse.getSenderId()).isEqualTo("12ngg3nfj23");
    }
}