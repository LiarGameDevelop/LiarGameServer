package com.game.liar.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;

//https://see-ro-e.tistory.com/340
//@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonDeserialize(using = CustomDeserializer.class)
public class MessageBody {
}