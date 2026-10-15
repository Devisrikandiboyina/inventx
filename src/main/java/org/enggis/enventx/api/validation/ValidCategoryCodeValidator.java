package org.enggis.enventx.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.enggis.enventx.api.domain.enums.CategoryCode;

import java.util.Arrays;

public class ValidCategoryCodeValidator implements ConstraintValidator<ValidCategoryCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return Arrays.stream(CategoryCode.values()).anyMatch(code -> code.name().equals(value));
    }
}
