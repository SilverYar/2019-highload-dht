package ru.mail.polis.dao;

import java.io.IOException;

public class DAOException extends IOException {
    private static final long serialVersionUID = 111010L;

    public DAOException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
