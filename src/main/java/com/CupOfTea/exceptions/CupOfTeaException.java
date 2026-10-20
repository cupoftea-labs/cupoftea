package com.CupOfTea.exceptions;

public class CupOfTeaException extends IllegalArgumentException {

    public CupOfTeaException(String message) {
        super(message);
    }

    public CupOfTeaException(String message, Throwable cause) {
        super(message, cause);
    }

    public CupOfTeaException(Throwable cause) {
        super(cause);
    }
}
