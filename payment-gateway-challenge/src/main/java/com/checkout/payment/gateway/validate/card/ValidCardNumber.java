package com.checkout.payment.gateway.validate.card;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = CardNumberValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCardNumber {
  String message() default "Invalid card number";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
}
