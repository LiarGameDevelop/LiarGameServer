package com.game.liar.room.domain;

import lombok.Getter;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;

@Getter
@ToString
@Table(name = "game_user")
@Entity
public class GameUser extends BaseEntity implements Serializable {
    @EmbeddedId
    private UserId userId;

    @Embedded
    private RoomId roomId;

    @Column
    private String username;

    @Column
    private String password;

    @Enumerated(EnumType.STRING)
    private Authority authority;

    @Column
    private String sessionId;

    protected GameUser() {
    }

    public GameUser(UserId userId, RoomId roomId, String username, String password, Authority authority) {
        this.userId = userId;
        this.roomId = roomId;
        this.username = username;
        this.password = password;
        this.authority = authority;
    }

    public void saveSession(String sessionId) {
        if (sessionId == null) throw new IllegalArgumentException("session id does not exist");
        this.sessionId = sessionId;
    }
}
