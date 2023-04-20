package com.game.liar.game.service;

import com.game.liar.game.domain.Global;
import com.game.liar.game.dto.MessageContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageService {
    private final RabbitTemplate messagingTemplate;

    public void sendPrivateMessage(String uuid, MessageContainer.Message message, String receiver, String roomId) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();
        log.info("Send private message. message: {}, [receiver:{}]", response, receiver);
        messagingTemplate.convertAndSend("message.direct", String.format("room.%s.user.%s", roomId, receiver), response);
    }

    public void sendPublicMessage(String uuid, MessageContainer.Message message, String roomId) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();
        log.info("Send public message. message: {}, [room:{}]", response, roomId);
        messagingTemplate.convertAndSend("amq.topic", String.format("room.%s.user.*", roomId), response);
    }

    public void sendErrorMessage(String uuid, MessageContainer.Message message, String receiver) {
        MessageContainer response = MessageContainer.messageContainerBuilder()
                .uuid(uuid)
                .senderId("SERVER")
                .message(message)
                .build();
        log.info("Send error message. message: {}, [receiver:{}]", response, receiver);
        messagingTemplate.convertAndSend("message.error", String.format("user.%s", receiver), response);
    }

    public void sendLoginInfoMessage(String roomId, Global.LoginInfo loginInfo) {
        if(loginInfo.isLogin())
            messagingTemplate.convertAndSend("amq.topic", String.format("room.%s.login", roomId), loginInfo);
        else
            messagingTemplate.convertAndSend("amq.topic", String.format("room.%s.logout", roomId), loginInfo);
    }
}
