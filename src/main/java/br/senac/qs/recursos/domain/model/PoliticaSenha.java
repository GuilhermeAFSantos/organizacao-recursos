package br.senac.qs.recursos.domain.model;

/**
 * Política de senha (RF01): pelo menos 8 caracteres, com ao menos uma letra e um número.
 * Regra única, usada na validação da API ({@code @SenhaForte}) e na criação do primeiro ADMINISTRADOR.
 */
public final class PoliticaSenha {

    public static final int TAMANHO_MINIMO = 8;
    public static final String DESCRICAO = "pelo menos 8 caracteres, com letras e números";

    private PoliticaSenha() {
    }

    public static boolean atende(String senha) {
        return senha != null
                && senha.length() >= TAMANHO_MINIMO
                && senha.chars().anyMatch(Character::isLetter)
                && senha.chars().anyMatch(Character::isDigit);
    }
}
