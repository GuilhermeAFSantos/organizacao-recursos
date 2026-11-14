package br.senac.qs.recursos.web.validacao;

import br.senac.qs.recursos.domain.model.PoliticaSenha;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Delega para a regra do domínio ({@link PoliticaSenha}); nulo fica a cargo do {@code @NotBlank}. */
public class SenhaForteValidator implements ConstraintValidator<SenhaForte, String> {

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext contexto) {
        return senha == null || PoliticaSenha.atende(senha);
    }
}
