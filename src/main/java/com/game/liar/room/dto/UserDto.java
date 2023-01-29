package com.game.liar.room.dto;

import com.game.liar.room.domain.GameUser;
import com.game.liar.room.domain.RoomId;
import com.game.liar.room.domain.UserId;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**Game User Data with password**/
@Getter
@Slf4j
@Setter
@AllArgsConstructor
@Builder
@ToString
public class UserDto {
    private String username;
    private String userId;
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
