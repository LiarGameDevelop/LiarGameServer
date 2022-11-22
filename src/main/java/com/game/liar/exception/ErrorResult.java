package com.game.liar.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@Getter
//@NoArgsConstructor
public class ErrorResult {
    private String code;
    private String message;
}
