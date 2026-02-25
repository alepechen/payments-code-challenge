package com.checkout.payment.gateway.validate.date;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ExpiryDateValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidExpiryDate {
  String message() default "Card expiry date must be in the future";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
}
