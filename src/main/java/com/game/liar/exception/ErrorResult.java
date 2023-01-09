package com.game.liar.exception;

import lombok.*;

@AllArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
public class ErrorResult {
    private String code;
    private String message;
}
