package com.game.liar.room.dto;

import com.game.liar.user.domain.GameUser;
import com.game.liar.room.domain.RoomId;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * Game User Data with password
 **/
@Getter
@Slf4j
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class UserDto {
    @ApiModelProperty(value = "유저 이름", example = "younghee")
    private String username;
    @ApiModelProperty(value = "유저 아이디", example = "05dec89b-7a3a-45b5-9c51-eca2a27bf604")
    private String userId;
    @ApiModelProperty(value = "토큰 비밀번호",notes = "token 만드는데 사용된 비밀번호", example = "05dec89b-7a3a-45b5-9c51-eca2a27bf604")
    private String password;

    public static UserDto toDto(GameUser user) {
        return UserDto.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .userId(user.getUserId().getUserId())
                .build();
    }

    public UsernamePasswordAuthenticationToken toAuthentication(RoomId roomId) {
        return new UsernamePasswordAuthenticationToken(roomId.getId() + userId, password);
    }
}
