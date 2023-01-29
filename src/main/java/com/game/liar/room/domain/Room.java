package com.game.liar.room.domain;

import com.game.liar.game.domain.RoomSettings;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.*;

@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(name = "room")
@ToString
public class Room {
    @EmbeddedId
    private RoomId id;
    @Embedded
    private RoomSettings settings;
    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "owner_id"))
    private UserId ownerId;

    public Room(RoomId id, RoomSettings settings, UserId ownerId) {
        setId(id);
        setSettings(settings);
        setOwnerId(ownerId);
    }

    private void setOwnerId(UserId ownerId) {
        if (ownerId == null) throw new IllegalArgumentException("no owner id");
        this.ownerId = ownerId;
    }

    private void setSettings(RoomSettings settings) {
        if (settings == null) throw new IllegalArgumentException("no room settings");
        this.settings = settings;
    }

    private void setId(RoomId id) {
        if (id == null) throw new IllegalArgumentException("no room id");
        this.id = id;
    }
}
