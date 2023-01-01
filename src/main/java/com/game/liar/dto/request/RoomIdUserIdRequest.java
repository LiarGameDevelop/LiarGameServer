package com.game.liar.dto.request;

import com.game.liar.dto.MessageBody;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class RoomIdUserIdRequest{
    @NotBlank
    private String roomId;

    private String userId;
}
