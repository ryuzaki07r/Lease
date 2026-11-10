package com.rahul.Lease.Exceptions;

public class PaymentException extends RuntimeException{
    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
