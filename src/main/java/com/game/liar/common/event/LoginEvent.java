package com.game.liar.common.event;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class LoginEvent {
    @Override
    public String toString() {
        return "LoginEvent:{" +
                "username:'" + username +
                ", time:" + time +
                '}';
    }

    private String username;
    private Date time;

    public LoginEvent(String username) {
        this.username = username;
        time = new Date();
    }
}