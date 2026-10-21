package com.cupoftea.exceptions;

public class SchemaMismatchException extends CupOfTeaException {

    public SchemaMismatchException(String message) {
        super(message);
    }

    public SchemaMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
