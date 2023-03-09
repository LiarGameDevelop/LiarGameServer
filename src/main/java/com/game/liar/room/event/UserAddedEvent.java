package com.game.liar.room.event;

import com.game.liar.room.dto.UserDataDto;
import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

@ToString
public class UserAddedEvent extends ApplicationEvent {
    @Getter
    private String roomId;
    @Getter
    private UserDataDto user;

    public UserAddedEvent(Object source, String roomId, UserDataDto user) {
        super(source);
        this.roomId = roomId;
        this.user = user;
    }
}