package com.game.liar.game.domain;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.game.liar.game.dto.MessageBase;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonTypeName("gameSettings")
@Embeddable
@Slf4j
public class GameSettings extends MessageBase {
    private Integer round;
    private Integer turn;

    @ElementCollection
    @CollectionTable(
            name = "settings_category",
            joinColumns = @JoinColumn(name = "SETTINGS_ID")
    )
    @Column(name = "CATEGORY")
    private List<String> category;

    @ElementCollection
    @CollectionTable(
            name = "selected_category",
            joinColumns = @JoinColumn(name = "SETTINGS_ID")
    )
    @MapKeyColumn(name = "category")
    @Getter
    private Map<String, ArrayList<String>> selectedByRoomOwnerCategory;

    @Builder
    public GameSettings(Integer round, Integer turn, List<String> category) {
        this.round = round;
        this.turn = turn;
        this.category = category;
    }

    public void initializeCategory(Map<String, List<String>> subjects, List<String> categoryList) {
        log.info("subjects = {}, categories = {}", subjects, categoryList);
        selectedByRoomOwnerCategory = new HashMap<>();

        for (String category : categoryList) {
            if (!this.category.contains(category))
                continue;
            ArrayList<String> predefinedKeywords = new ArrayList<>(subjects.get(category));
            selectedByRoomOwnerCategory.put(category, predefinedKeywords);
        }
    }
}
