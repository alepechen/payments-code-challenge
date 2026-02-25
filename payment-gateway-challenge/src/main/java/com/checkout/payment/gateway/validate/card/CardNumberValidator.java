package com.checkout.payment.gateway.validate.card;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CardNumberValidator implements ConstraintValidator<ValidCardNumber, String> {

  @Override
  public boolean isValid(String cardNumber, ConstraintValidatorContext context) {
    if (!cardNumber.matches("\\d+")) {
      context.buildConstraintViolationWithTemplate(
          "Card number must contain only numeric characters").addConstraintViolation();
      return false;
    }

    if (cardNumber.length() < 14 || cardNumber.length() > 19) {
      context.buildConstraintViolationWithTemplate("Card number must be between 14 and 19 digits")
          .addConstraintViolation();
      return false;
    }

    return true;
  }
}
