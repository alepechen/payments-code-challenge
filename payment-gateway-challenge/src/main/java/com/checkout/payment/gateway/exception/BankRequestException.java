package com.checkout.payment.gateway.exception;

public class BankRequestException extends RuntimeException {

  public BankRequestException(String message) {
    super(message);
  }
}
