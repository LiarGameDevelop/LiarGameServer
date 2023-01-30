package com.game.liar.game.domain;

import io.swagger.annotations.ApiModelProperty;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
@ToString
public class RoomSettings {
    protected RoomSettings() {
    }

    @Column
    @ApiModelProperty(example = "5")
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
