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

    @Nested
    @DisplayName("RC02: detecção de sobreposição, intervalos semiabertos [início, fim)")
    class Sobreposicao {

        /*
         * REGRA EXPLÍCITA: intervalos que apenas se TOCAM (fim de um = início do outro) NÃO se
         * sobrepõem. Uma aula das 08h às 10h e outra das 10h às 12h na mesma sala são válidas.
         * Cada par é verificado nos dois sentidos: a relação precisa ser simétrica.
         */
        @ParameterizedTest(name = "A=[{0}–{1}) × B=[{2}–{3}) → sobrepõe={4} ({5})")
        @CsvSource(delimiter = '|', textBlock = """
            08:00:00 | 10:00:00 | 10:00:00 | 12:00:00 | false | B começa exatamente quando A termina (só se tocam)
            10:00:00 | 12:00:00 | 08:00:00 | 10:00:00 | false | A começa exatamente quando B termina (só se tocam)
            08:00:00 | 10:00:00 | 10:00:01 | 12:00:00 | false | separados por 1 segundo
            08:00:00 | 10:00:00 | 13:00:00 | 14:00:00 | false | totalmente separados
            08:00:00 | 10:00:01 | 10:00:00 | 12:00:00 | true  | cruzam por 1 segundo
            08:00:00 | 10:00:00 | 09:00:00 | 11:00:00 | true  | sobreposição parcial
            08:00:00 | 12:00:00 | 09:00:00 | 10:00:00 | true  | B contido em A
            08:00:00 | 10:00:00 | 08:00:00 | 09:00:00 | true  | mesmo início
            08:00:00 | 10:00:00 | 09:00:00 | 10:00:00 | true  | mesmo término
            08:00:00 | 10:00:00 | 08:00:00 | 10:00:00 | true  | intervalos idênticos
            """)
        void detectaSobreposicao(String inicioA, String fimA, String inicioB, String fimB,
                                 boolean esperado, String caso) {
            var a = periodo(inicioA, fimA);
            var b = periodo(inicioB, fimB);

            assertThat(a.sobrepoe(b)).as("A × B").isEqualTo(esperado);
            assertThat(b.sobrepoe(a)).as("B × A (simetria)").isEqualTo(esperado);
        }

        @Test
        @DisplayName("mesmo instante escrito em fusos diferentes conta como sobreposição")
        void sobreposicaoEntreFusos() {
            var emBrasilia = new PeriodoReserva(instante("2026-12-01T08:00:00-03:00"), instante("2026-12-01T10:00:00-03:00"));
            var emUtc = new PeriodoReserva(instante("2026-12-01T12:59:59Z"), instante("2026-12-01T14:00:00Z"));

            assertThat(emBrasilia.sobrepoe(emUtc)).isTrue();
        }

        private static PeriodoReserva periodo(String horaInicio, String horaFim) {
            return new PeriodoReserva(instante("2026-12-01T" + horaInicio + "-03:00"),
                                      instante("2026-12-01T" + horaFim + "-03:00"));
        }
    }

    @Nested
    @DisplayName("Descrição para mensagens (horário de Brasília)")
    class Descricao {

        @Test
        @DisplayName("no mesmo dia: data uma vez só")
        void mesmoDia() {
            var periodo = new PeriodoReserva(instante("2026-12-01T10:00-03:00"), instante("2026-12-01T12:00-03:00"));

            assertThat(periodo.descrever()).isEqualTo("01/12/2026 10:00–12:00");
        }

        @Test
        @DisplayName("virando o dia: as duas datas; o instante em UTC é exibido no horário local")
        void viraODia() {
            var periodo = new PeriodoReserva(instante("2026-12-02T01:00:00Z"), instante("2026-12-02T04:00:00Z"));

            assertThat(periodo.descrever()).isEqualTo("01/12/2026 22:00–02/12/2026 01:00");
        }
    }

    static Instant instante(String isoComFuso) {
        return OffsetDateTime.parse(isoComFuso).toInstant();
    }
}
