package com.checkout.payment.gateway.validate.date;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import java.time.YearMonth;

public class ExpiryDateValidator implements
    ConstraintValidator<ValidExpiryDate, PostPaymentRequest> {

  @Override
  public boolean isValid(PostPaymentRequest request, ConstraintValidatorContext context) {
    Integer month = request.getExpiryMonth();
    Integer year = request.getExpiryYear();
    // Year + month must be in the future
    YearMonth expiry = YearMonth.of(year, month);
    LocalDate expiryDate = expiry.atEndOfMonth();

    if (expiryDate.isBefore(LocalDate.now())) {
      context.buildConstraintViolationWithTemplate("Card has expired").addPropertyNode("expiryYear")
          .addConstraintViolation();
      return false;
    }
    return true;
  }
}
