package com.game.liar.exception;

public class JsonDeserializeException extends LiarGameException{
    public JsonDeserializeException(String message) {
        super(message, "Json Deserialize Error");
    }
}
