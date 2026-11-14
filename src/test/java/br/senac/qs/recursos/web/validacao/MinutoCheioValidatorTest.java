package br.senac.qs.recursos.web.validacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Q3: horários só em minutos cheios")
class MinutoCheioValidatorTest {

    final MinutoCheioValidator validador = new MinutoCheioValidator();

    @ParameterizedTest(name = "{0} → válido={1} ({2})")
    @CsvSource(delimiter = '|', textBlock = """
        2026-12-01T08:00:00-03:00     | true  | hora cheia
        2026-12-01T08:15:00-03:00     | true  | minuto cheio
        2026-12-01T23:59:00-03:00     | true  | último minuto do dia
        2026-12-01T08:00:01-03:00     | false | 1 segundo
        2026-12-01T08:00:59-03:00     | false | 59 segundos
        2026-12-01T08:00:00.001-03:00 | false | fração de segundo
        """)
    void validaMinutoCheio(String horario, boolean valido, String caso) {
        assertThat(validador.isValid(OffsetDateTime.parse(horario), null)).isEqualTo(valido);
    }

    @Test
    @DisplayName("nulo é aceito (a obrigatoriedade é do @NotNull)")
    void nuloAceito() {
        assertThat(validador.isValid(null, null)).isTrue();
    }
}
