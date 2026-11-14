package br.senac.qs.recursos.web.validacao;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Q3: horários de reservas e bloqueios só em minutos cheios (segundos e frações iguais a zero).
 * Evita reservas como 08:00:01 e deixa as fronteiras entre períodos previsíveis. Nulo é aceito
 * aqui (a obrigatoriedade fica com {@code @NotNull}).
 */
@Documented
@Constraint(validatedBy = MinutoCheioValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MinutoCheio {

    String message() default "deve estar em minutos cheios, sem segundos (ex.: 08:00, 08:15)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
