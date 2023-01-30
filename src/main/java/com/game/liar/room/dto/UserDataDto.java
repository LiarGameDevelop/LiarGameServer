package com.game.liar.room.dto;

import com.game.liar.exception.NotExistException;
import com.game.liar.room.domain.GameUser;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**Game User data without password**/
@Getter
@Slf4j
@Setter
@AllArgsConstructor
@Builder
public class UserDataDto {
    @ApiModelProperty(value="유저 이름",example = "younghee")
    private String username;
    @ApiModelProperty(value="유저 아이디",example = "05dec89b-7a3a-45b5-9c51-eca2a27bf604")
    private String userId;

    public static UserDataDto toDto(GameUser user) {
        if (user == null) throw new NotExistException("User does not exist");
        return UserDataDto.builder()
                .username(user.getUsername())
                .userId(user.getUserId().getUserId())
                .build();
    }

    public static UserDataDto toDto(UserDto user) {
        return UserDataDto.builder()
                .username(user.getUsername())
                .userId(user.getUserId())
                .build();
    }
}
