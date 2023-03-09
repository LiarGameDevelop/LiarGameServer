package com.game.liar.room.domain;

import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
@ToString
public class RoomId implements Serializable {
    //private static final long serialVersionUID = -3438522768010613027L;

    @Column(name = "room_id")
    private String id;

    protected RoomId(){}

    public RoomId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoomId roomId = (RoomId) o;
        return Objects.equals(id, roomId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public String getId() {
        return id;
    }

    public static RoomId of(String id) {
        return new RoomId(id);
    }
}
