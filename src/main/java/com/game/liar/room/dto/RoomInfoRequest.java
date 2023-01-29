package com.game.liar.room.dto;

import lombok.*;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RoomInfoRequest{
    @NotNull
    private Integer maxPersonCount;
    @NotNull
    private String ownerName;
    @NotNull
    private String password;
}
