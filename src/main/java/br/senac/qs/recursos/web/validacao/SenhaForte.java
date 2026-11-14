package br.senac.qs.recursos.web.validacao;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Política de senha (RF01): pelo menos 8 caracteres, com ao menos uma letra e um número.
 * Nulo é aceito aqui (a obrigatoriedade fica com {@code @NotBlank}).
 */
@Documented
@Constraint(validatedBy = SenhaForteValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SenhaForte {

    String message() default "deve ter pelo menos 8 caracteres, com letras e números";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
