package com.game.liar.game.domain;

import lombok.ToString;
import org.hibernate.Hibernate;

import javax.persistence.*;
import java.util.Objects;

@Table
@Entity
@ToString
public class GameSubject implements Comparable<GameSubject>{
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String keyword;

    @Column(nullable = false)
    private String category;

    protected GameSubject() {
    }

    public GameSubject(String category,String keyword) {
        setCategory(category);
        setKeyword(keyword);
    }

    private void setCategory(String category) {
        if (category == null) throw new IllegalArgumentException("category field should be required");
        this.category = category;
    }

    private void setKeyword(String keyword) {
        if (keyword == null) throw new IllegalArgumentException("keyword field should be required");
        this.keyword = keyword;
    }

    public Long getId() {
        return id;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        GameSubject subject = (GameSubject) o;
        return id != null && Objects.equals(id, subject.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public int compareTo(GameSubject o) {
        if(category.compareTo(o.category) == 0){
            return keyword.compareTo(o.keyword);
        }
        return category.compareTo(o.category);
    }
}
