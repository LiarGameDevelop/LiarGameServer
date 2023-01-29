package com.game.liar.room.dto;

import com.game.liar.exception.NotExistException;
import com.game.liar.room.domain.GameUser;
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
    private String username;
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
