package com.game.liar.controller;

import com.game.liar.Util;
import com.game.liar.chat.domain.ChatMessageDto;
import com.game.liar.chat.repository.ChatRepository;
import com.game.liar.game.domain.Global;
import com.game.liar.websocket.InboundInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeoutException;

import static com.game.liar.Util.createStompObj;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChattingControllerTest {
    //public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0");

    @LocalServerPort
    private Integer port;
    @Autowired
    ChatRepository chatRepository;
    @Autowired
    private MockMvc mockMvc;
    //@MockBean
    //private InboundInterceptor inboundInterceptor;

    @BeforeEach
    void clear() {
        //when(inboundInterceptor.preSend(any(), any())).thenAnswer(i -> i.getArguments()[0]);
        chatRepository.deleteAll();
    }

    @Test
    public void 채팅채널에_메세지를보내면_메세지를받고_db에저장된다() throws Exception {
        //given
        Util.TestStompObject obj1 = createStompObj(mockMvc, port);
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
    public void 채팅서비스_여러개_서로_간섭하지않아야한다() throws Exception {
        Util.TestStompObject obj1 = createStompObj(mockMvc, port);
        Util.TestStompObject obj2 = createStompObj(mockMvc, port);
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

        assertThrows(TimeoutException.class, () -> handler2.getCompletableFuture().get(3, SECONDS));
    }
}