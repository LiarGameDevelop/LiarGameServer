package com.game.liar.user.domain;

import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
@ToString
public class UserId implements Serializable {
    @Column(name="USER_ID")
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
        UserId userId1 = (UserId) o;
        return userId.equals(userId1.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    public static UserId of(String id) {
        return new UserId(id);
    }
}
