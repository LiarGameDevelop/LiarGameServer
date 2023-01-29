package com.game.liar.room.domain;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserId implements Serializable {
    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Column(name="user_id")
    private String userId;

    protected UserId() {
    }

    public UserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserId userId = (UserId) o;
        return Objects.equals(userId, userId.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    public static UserId of(String id) {
        return new UserId(id);
    }
}
