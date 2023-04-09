package com.game.liar.controller;

import com.game.liar.Util;
import com.game.liar.chat.domain.ChatMessageDto;
import com.game.liar.chat.repository.ChatRepository;
import com.game.liar.game.domain.Global;
import com.game.liar.room.domain.Room;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeoutException;

import static com.game.liar.Util.createRoomAndStompObj;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("error in rabbitmq connection")
class ChattingControllerTest {

    @LocalServerPort
    private Integer port;
    @Autowired
    ChatRepository chatRepository;
    @Autowired
    RoomRepository roomRepository;
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void clear() {
        chatRepository.deleteAll();
    }

    @Test
    @DisplayName("채팅채널에_메세지를보내면_메세지를받고_db에저장된다")
    public void verifyMessageFromChannelDBSave() throws Exception {
        //given
        Util.TestStompObject obj1 = createRoomAndStompObj(mockMvc, port);
        Util.PrivateStompHandler<ChatMessageDto> handler = new Util.PrivateStompHandler<>(ChatMessageDto.class);
        obj1.subscribe(handler, String.format("/topic/room.%s.chat", obj1.getRoomInfo().getRoom().getRoomId()));

        //when
        ChatMessageDto expectedMessage = new ChatMessageDto("abc", "hello", Global.MessageType.MESSAGE);
        obj1.getStompSession().send(String.format("/publish/messages.%s", obj1.getRoomInfo().getRoom().getRoomId()), expectedMessage);

        //then
        ChatMessageDto message = handler.getCompletableFuture().get(5, SECONDS);

        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectedMessage);
        System.out.println(chatRepository.findAll());
        assertThat(chatRepository.findAll().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제된 방에 메세지가 전달될 경우 무시한다")
    public void verifyIgnoreMessageFromNoRoom() throws Exception {
        //given
        Util.TestStompObject obj1 = createRoomAndStompObj(mockMvc, port);
        Util.PrivateStompHandler<ChatMessageDto> handler = new Util.PrivateStompHandler<>(ChatMessageDto.class);
        obj1.subscribe(handler, String.format("/topic/room.%s.chat", obj1.getRoomInfo().getRoom().getRoomId()));

        //when
        Room room = roomRepository.findById(RoomId.of(obj1.getRoomInfo().getRoom().getRoomId())).get();
        roomRepository.delete(room);
        ChatMessageDto expectedMessage = new ChatMessageDto("abc", "hello", Global.MessageType.MESSAGE);
        obj1.getStompSession().send(String.format("/publish/messages.%s", obj1.getRoomInfo().getRoom().getRoomId()), expectedMessage);

        //then
        assertThrows(TimeoutException.class, () -> handler.getCompletableFuture().get(3, SECONDS));
        System.out.println(chatRepository.findAll());
        assertThat(chatRepository.findAll().size()).isEqualTo(0);
    }

    @Test
    @DisplayName("채팅채널 여러개가 서로 간섭하지 않아야한다")
    public void verifyDoNotDisturbAnotherChattingChannel() throws Exception {
        Util.TestStompObject obj1 = createRoomAndStompObj(mockMvc, port);
        Util.TestStompObject obj2 = createRoomAndStompObj(mockMvc, port);
        //given
        Util.PrivateStompHandler<ChatMessageDto> handler = new Util.PrivateStompHandler<>(ChatMessageDto.class);
        obj1.subscribe(handler, String.format("/topic/room.%s.chat", obj1.getRoomInfo().getRoom().getRoomId()));

        ChatMessageDto expectedMessage = new ChatMessageDto("abc", "hello", Global.MessageType.MESSAGE);
        obj1.getStompSession().send(String.format("/publish/messages.%s", obj1.getRoomInfo().getRoom().getRoomId()), expectedMessage);

        //when
        Util.PrivateStompHandler<ChatMessageDto> handler2 = new Util.PrivateStompHandler<>(ChatMessageDto.class);
        obj2.subscribe(handler2, String.format("/topic/room.%s.chat", obj2.getRoomInfo().getRoom().getRoomId()));

        //then
        ChatMessageDto message = handler.getCompletableFuture().get(5, SECONDS);
        assertThat(obj1.getStompSession()).isNotNull();
        assertThat(obj2.getStompSession()).isNotNull();
        assertThat(message).isNotNull();
        assertThat(message).isEqualTo(expectedMessage);

        assertThrows(TimeoutException.class, () -> handler2.getCompletableFuture().get(5, SECONDS));
    }
}