package com.thecircle.users.service;

/** Thrown on login when no account exists for the supplied email. */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
