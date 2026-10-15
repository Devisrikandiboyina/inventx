package org.enggis.enventx.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = AllowedEmailDomainValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedEmailDomain {

    String message() default "Email must end with @enggis.com, @nexsys.co.in or @stability.co";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
