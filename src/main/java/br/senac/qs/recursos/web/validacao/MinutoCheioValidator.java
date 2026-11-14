package br.senac.qs.recursos.web.validacao;

import java.time.OffsetDateTime;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MinutoCheioValidator implements ConstraintValidator<MinutoCheio, OffsetDateTime> {

    @Override
    public boolean isValid(OffsetDateTime horario, ConstraintValidatorContext contexto) {
        return horario == null || (horario.getSecond() == 0 && horario.getNano() == 0);
    }
}
