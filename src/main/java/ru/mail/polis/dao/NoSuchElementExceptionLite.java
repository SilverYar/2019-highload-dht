package ru.mail.polis.dao;

import java.util.NoSuchElementException;

@SuppressWarnings("serial")
public class NoSuchElementExceptionLite extends NoSuchElementException {

    NoSuchElementExceptionLite(final String s) {
        super(s);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
