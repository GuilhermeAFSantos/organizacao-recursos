package br.senac.qs.recursos.web.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SenhaForteValidator implements ConstraintValidator<SenhaForte, String> {

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext contexto) {
        if (senha == null) {
            return true;
        }
        return senha.length() >= SenhaForte.TAMANHO_MINIMO
                && senha.chars().anyMatch(Character::isLetter)
                && senha.chars().anyMatch(Character::isDigit);
    }
}
