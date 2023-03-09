package com.game.liar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.liar.room.dto.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RoomControllerTest {
//    @Mock
//    private GameController gameController;
//
//    @InjectMocks
//    private RoomController roomController;
//
//    @Mock
//    private RoomService roomService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Transactional
    public void 방생성() throws Exception {
        //Given
        createRoom();

        //When
        //Then
    }

    private EnterRoomResponse createRoom() throws Exception {
        ObjectMapper om = new ObjectMapper();
        RoomInfoRequest request = new RoomInfoRequest(5, "tester", "password");
        String result = mockMvc.perform(
                        post("/room/create")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        System.out.println("result =" + result);
        return om.readValue(result, EnterRoomResponse.class);
    }

    private EnterRoomResponse enterRoom(String roomId, String username) throws Exception {
        ObjectMapper om = new ObjectMapper();
        RoomIdUserNameRequest request = new RoomIdUserNameRequest(roomId, username, "password");
        String result = mockMvc.perform(
                        post("/room/enter")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        System.out.println("result =" + result);
        return om.readValue(result, EnterRoomResponse.class);
    }

    private EnterRoomResponse enterRoomError(String roomId, String username) throws Exception {
        ObjectMapper om = new ObjectMapper();
        RoomIdUserNameRequest request = new RoomIdUserNameRequest(roomId, username, "password");
        String result = mockMvc.perform(
                        post("/room/enter")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        System.out.println("result =" + result);
        return om.readValue(result, EnterRoomResponse.class);
    }

    private RoomInfoResponse getRoom(String roomId, String accessToken) throws Exception {
        ObjectMapper om = new ObjectMapper();
        String result = mockMvc.perform(
                        get("/room/info")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .param("roomId", roomId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        System.out.println("result =" + result);
        return om.readValue(result, RoomInfoResponse.class);
    }

    @Test
    @Transactional
    public void 방입장() throws Exception {
        //Given
        EnterRoomResponse roomInfoFromOwner = createRoom();
        String roomId = roomInfoFromOwner.getRoom().getRoomId();
        String accessTokenFromOwner = roomInfoFromOwner.getToken().getAccessToken();

        //When
        EnterRoomResponse roomInfoFromGuest = enterRoom(roomId, "guest1");

        //Then
        RoomInfoResponse roomInfo = getRoom(roomId, accessTokenFromOwner);
        assertThat(roomInfo.getRoom().getRoomId()).isEqualTo(roomId);
        assertThat(roomInfo.getUserList().size()).isEqualTo(2);
        assertThat(roomInfo.getUserList()).contains(UserDataDto.toDto(roomInfoFromOwner.getUser()), UserDataDto.toDto(roomInfoFromGuest.getUser()));
    }

    @Test
    @Transactional
    @DisplayName("최대인원_방입장")
    public void enterRoomMaxCount() throws Exception {
        //Given
        EnterRoomResponse roomInfoFromOwner = createRoom();
        String roomId = roomInfoFromOwner.getRoom().getRoomId();
        String accessTokenFromOwner = roomInfoFromOwner.getToken().getAccessToken();

        //When
        EnterRoomResponse roomInfoFromGuest = enterRoom(roomId, "guest1");
        EnterRoomResponse roomInfoFromGuest2 = enterRoom(roomId, "guest2");
        EnterRoomResponse roomInfoFromGuest3 = enterRoom(roomId, "guest3");
        EnterRoomResponse roomInfoFromGuest4 = enterRoom(roomId, "guest4");

        //Then
        assertThat(roomInfoFromGuest.getToken().getAccessToken()).isNotEqualTo(accessTokenFromOwner);
        ObjectMapper om = new ObjectMapper();
        RoomIdUserNameRequest request = new RoomIdUserNameRequest(roomId, "guest5NotPermitted", "password");
        String result = mockMvc.perform(
                        post("/room/enter")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        System.out.println("result =" + result);
    }

    @Test
    @Transactional
    @DisplayName("방나가기")
    @Disabled("API deprecated")
    public void leaveRoom() throws Exception {
        //Given
        EnterRoomResponse roomInfoFromOwner = createRoom();
        String roomId = roomInfoFromOwner.getRoom().getRoomId();
        String accessTokenFromOwner = roomInfoFromOwner.getToken().getAccessToken();

        //When
        EnterRoomResponse roomInfoFromGuest = enterRoom(roomId, "guest1");
        EnterRoomResponse roomInfoFromGuest2 = enterRoom(roomId, "guest2");
        EnterRoomResponse roomInfoFromGuest3 = enterRoom(roomId, "guest3");
        EnterRoomResponse roomInfoFromGuest4 = enterRoom(roomId, "guest4");

        //Then
        assertThat(roomInfoFromGuest.getToken().getAccessToken()).isNotEqualTo(accessTokenFromOwner);
        ObjectMapper om = new ObjectMapper();
        RoomIdUserIdRequest request = new RoomIdUserIdRequest(roomId, roomInfoFromGuest.getUser().getUserId());
        String result = mockMvc.perform(
                        post("/room/leave")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        System.out.println("result =" + result);
    }
}
