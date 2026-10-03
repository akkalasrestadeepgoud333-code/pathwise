package app.pathwise.web.platform;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bean Validation for form and request JavaBeans. Returns field → first message. */
public final class Validation {
    // ParameterMessageInterpolator avoids needing an Expression Language implementation at runtime.
    private static final Validator VALIDATOR = jakarta.validation.Validation.byProvider(HibernateValidator.class)
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()
            .getValidator();

    private Validation() {}

    public static Map<String, String> validate(Object bean) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<Object> v : VALIDATOR.validate(bean)) {
            errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage());
        }
        return errors;
    }
}
