package com.game.liar.dto;

import com.game.liar.exception.MaxCountException;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

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
    private String roomName;
    @NotNull
    private String ownerId;
    private final List<Member> memberList = new ArrayList<>();

    public boolean addMember(Member member) throws MaxCountException {
        if (memberList.size() + 1 <= maxCount) {
            memberList.add(member);
            return true;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("The maximum number(");
        sb.append(maxCount.toString());
        sb.append(") of people in the room.");
        throw new MaxCountException(sb.toString());
    }

    public boolean leaveMember(Member member){
        memberList.remove(member);
        return true;
    }
}
