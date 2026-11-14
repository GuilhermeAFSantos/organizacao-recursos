package br.senac.qs.recursos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;

import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("PeriodoReserva")
class PeriodoReservaTest {

    @Nested
    @DisplayName("RC01: o término deve ser posterior ao início")
    class TerminoPosteriorAoInicio {

        @ParameterizedTest(name = "{2}: [{0} → {1}] é rejeitado")
        @CsvSource(delimiter = '|', textBlock = """
            2026-12-01T10:00:00-03:00 | 2026-12-01T10:00:00-03:00 | término igual ao início
            2026-12-01T10:00:00-03:00 | 2026-12-01T13:00:00Z      | duração zero com fusos diferentes (mesmo instante)
            2026-12-01T10:00:00-03:00 | 2026-12-01T09:59:59-03:00 | término 1 segundo antes do início
            2026-12-01T10:00:00-03:00 | 2026-12-01T08:00:00-03:00 | término 2 horas antes do início
            2026-12-01T23:00:00-03:00 | 2026-12-02T01:00:00Z      | término com data maior, mas instante anterior (outro fuso)
            """)
        void rejeitaTerminoQueNaoEPosterior(String inicio, String fim, String caso) {
            assertThatThrownBy(() -> new PeriodoReserva(instante(inicio), instante(fim)))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("O término da reserva deve ser posterior ao início.");
        }

        @ParameterizedTest(name = "{2}: [{0} → {1}] é aceito")
        @CsvSource(delimiter = '|', textBlock = """
            2026-12-01T10:00:00-03:00 | 2026-12-01T10:00:01-03:00 | menor duração válida (1 segundo)
            2026-12-01T10:00:00-03:00 | 2026-12-01T13:00:01Z      | 1 segundo, com fusos diferentes
            2026-12-01T08:00:00-03:00 | 2026-12-01T10:00:00-03:00 | duração comum de aula
            """)
        void aceitaTerminoPosterior(String inicio, String fim, String caso) {
            var periodo = new PeriodoReserva(instante(inicio), instante(fim));

            assertThat(periodo.inicio()).isEqualTo(instante(inicio));
            assertThat(periodo.fim()).isEqualTo(instante(fim));
        }

        @Test
        @DisplayName("início e término são obrigatórios")
        void rejeitaNulos() {
            Instant agora = instante("2026-12-01T10:00:00-03:00");

            assertThatThrownBy(() -> new PeriodoReserva(null, agora))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("O início da reserva é obrigatório.");
            assertThatThrownBy(() -> new PeriodoReserva(agora, null))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("O término da reserva é obrigatório.");
        }
    }

    static Instant instante(String isoComFuso) {
        return OffsetDateTime.parse(isoComFuso).toInstant();
    }
}
