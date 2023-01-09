package com.game.liar.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
public class StompExceptionHandler {
    public StompExceptionHandler(Throwable ex) {
        super();
    }

    public Message<byte[]> handleException(Message<byte[]> clientMessage, Throwable ex) {
        log.info("message : {}, ex :{}", clientMessage, ex);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);

        accessor.setMessage(String.valueOf(clientMessage));
        //accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(ex.getMessage().getBytes(StandardCharsets.UTF_8), accessor.getMessageHeaders());
    }
}
