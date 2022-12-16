package com.game.liar.domain;

import com.game.liar.exception.MaxCountException;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Room {
    @NotNull
    private Integer maxCount;
    @NotNull
    private String roomId;
    @NotNull
    private String ownerName;
    @NotNull
    private String ownerId;
    private final List<User> userList = new ArrayList<>();

    public boolean addMember(User user) throws MaxCountException {
        if (userList.size() + 1 <= maxCount) {
            userList.add(user);
            return true;
        }
        throw new MaxCountException(String.format("The maximum number(%s) of people in the room.", maxCount.toString()));
    }

    public boolean leaveMember(String username) {
        Optional<User> member = userList.stream().filter(m -> m.getUsername().equals(username)).findFirst();
        if (member.isPresent()) {
            userList.remove(member.get());
            return true;
        }
        return false;
    }
}
