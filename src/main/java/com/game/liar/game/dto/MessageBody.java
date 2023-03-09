package com.game.liar.game.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

//https://see-ro-e.tistory.com/340
//@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonDeserialize(using = CustomDeserializer.class)
public class MessageBody {
}