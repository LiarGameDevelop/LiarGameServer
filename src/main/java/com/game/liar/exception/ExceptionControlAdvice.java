package com.game.liar.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice
public class ExceptionControlAdvice {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler
    public ErrorResult AlreadyExistExceptionHandle(AlreadyExistException e) {
        return new ErrorResult("Already Exist parameter", e.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler
    public ErrorResult NotExistExceptionHandle(NotExistException e) {
        return new ErrorResult("Not Exist parameter", e.getMessage());
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResult> NotAllowedExceptionHandle(NotAllowedActionException e){
        return ResponseEntity.badRequest().body(new ErrorResult("Not Allowed action", e.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<Set<ErrorResult>> handleMethodArgumentValidException(MethodArgumentNotValidException e) {
        Set<ErrorResult> errors = new HashSet<>();
        System.out.println(e.getBindingResult());
        e.getBindingResult().getAllErrors()
                .forEach(error ->
                        errors.add(new ErrorResult(
                                ((FieldError) error).getField(), error.getDefaultMessage())));
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResult> MaxCountExceptionHandle(MaxCountException e){
        return ResponseEntity.badRequest().body(new ErrorResult("Requested count is over", e.getMessage()));
    }
}