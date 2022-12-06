package com.game.liar.domain.request;

import lombok.*;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RoomInfoRequest {
    @NotNull
    private Integer maxPersonCount;
    @NotNull
    private String ownerName;
}
