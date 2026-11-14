package br.senac.qs.recursos.web.validacao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Política de senha: mínimo 8 caracteres, com letra e número")
class SenhaForteValidatorTest {

    final SenhaForteValidator validador = new SenhaForteValidator();

    @ParameterizedTest(name = "[{0}] → válida={1} ({2})")
    @CsvSource(delimiter = '|', textBlock = """
        abcde123   | true  | exatamente 8, com letra e número
        Senha2026  | true  | maiúsculas e minúsculas com número
        ção12345   | true  | letra acentuada conta como letra
        abcd123    | false | 7 caracteres (um a menos que o mínimo)
        abcdefgh   | false | 8 letras, sem número
        12345678   | false | 8 números, sem letra
        '        ' | false | só espaços
        """)
    void validaPolitica(String senha, boolean valida, String caso) {
        assertThat(validador.isValid(senha, null)).isEqualTo(valida);
    }

    @Test
    @DisplayName("nula é aceita (a obrigatoriedade é do @NotBlank)")
    void nulaAceita() {
        assertThat(validador.isValid(null, null)).isTrue();
    }
}
