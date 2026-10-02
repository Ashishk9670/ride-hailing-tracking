package com.example.ridehailing.service;

/** A location record that can never be processed (retrying is pointless; goes straight to the DLT). */
public class InvalidLocationException extends RuntimeException {
    public InvalidLocationException(String message) { super(message); }
}
