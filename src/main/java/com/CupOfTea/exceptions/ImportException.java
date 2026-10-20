package com.CupOfTea.exceptions;

public class ImportException extends CupOfTeaException {

    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
