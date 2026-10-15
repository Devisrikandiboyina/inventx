package org.enggis.enventx.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;
import java.util.Locale;

public class AllowedEmailDomainValidator implements ConstraintValidator<AllowedEmailDomain, String> {

    public static final List<String> ALLOWED_DOMAINS = List.of("@enggis.com", "@nexsys.co.in", "@stability.co");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        var email = value.strip().toLowerCase(Locale.ROOT);
        return ALLOWED_DOMAINS.stream().anyMatch(domain -> email.endsWith(domain) && email.length() > domain.length());
    }
}
