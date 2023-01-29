package com.game.liar.game.domain;

import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
@ToString
public class RoomSettings {
    protected RoomSettings() {
    }

    @Column
    private int maxCount;

    public RoomSettings(int maxCount) {
        this.maxCount = maxCount;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public static RoomSettings of(int maxCount){
        return new RoomSettings(maxCount);
    }

}
