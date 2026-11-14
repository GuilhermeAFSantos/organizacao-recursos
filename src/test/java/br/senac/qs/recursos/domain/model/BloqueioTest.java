package br.senac.qs.recursos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Bloqueios de recurso (RF08, RC04, Q9, Q10). Manutenção da Sala 101 das 10:00 às 12:00 (Brasília)
 * = 13:00Z às 15:00Z.
 */
@DisplayName("Bloqueio: manutenção e bloqueio administrativo")
class BloqueioTest {

    static final Instant INICIO = Instant.parse("2026-12-01T13:00:00Z");
    static final Instant FIM = Instant.parse("2026-12-01T15:00:00Z");
    static final Instant ANTES = Instant.parse("2026-12-01T10:00:00Z");

    final Usuario adm = new Usuario("adm@senac.br", Perfil.ADMINISTRADOR);
    final Sala sala = new Sala("Sala 101", false);

    Bloqueio manutencao() {
        return Bloqueio.registrar(adm, sala, TipoBloqueio.MANUTENCAO, new PeriodoReserva(INICIO, FIM),
                "Troca do projetor", ANTES);
    }

    @Nested
    @DisplayName("Registro")
    class Registro {

        @ParameterizedTest(name = "tipo {0}")
        @EnumSource(TipoBloqueio.class)
        @DisplayName("Q9: ADMINISTRADOR registra MANUTENCAO ou ADMINISTRATIVO, ocupando o período do recurso")
        void administradorRegistra(TipoBloqueio tipo) {
            var bloqueio = Bloqueio.registrar(adm, sala, tipo, new PeriodoReserva(INICIO, FIM), "Motivo", ANTES);

            assertThat(bloqueio.getTipo()).isEqualTo(tipo);
            assertThat(bloqueio.isAtivo()).isTrue();
            assertThat(bloqueio.getOcupacoes()).singleElement().satisfies(o -> {
                assertThat(o.getRecurso()).isSameAs(sala);
                assertThat(o.getPeriodo()).isEqualTo(new PeriodoReserva(INICIO, FIM));
                assertThat(o.isBloqueante()).isTrue();
            });
        }

        @ParameterizedTest(name = "perfil {0} → recusado")
        @ValueSource(strings = {"SOLICITANTE", "RESPONSAVEL"})
        @DisplayName("MN-08: só ADMINISTRADOR registra bloqueios")
        void soAdministrador(Perfil perfil) {
            var outro = new Usuario("outro@senac.br", perfil);

            assertThatThrownBy(() -> Bloqueio.registrar(outro, sala, TipoBloqueio.MANUTENCAO,
                    new PeriodoReserva(INICIO, FIM), "Motivo", ANTES))
                    .isInstanceOf(OperacaoNaoPermitidaException.class)
                    .hasMessage("Somente um ADMINISTRADOR pode registrar ou encerrar bloqueios.");
        }

        @ParameterizedTest(name = "motivo [{0}] → recusado")
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        @DisplayName("motivo é obrigatório")
        void motivoObrigatorio(String motivo) {
            assertThatThrownBy(() -> Bloqueio.registrar(adm, sala, TipoBloqueio.MANUTENCAO,
                    new PeriodoReserva(INICIO, FIM), motivo, ANTES))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Informe o motivo do bloqueio.");
        }
    }

    @Nested
    @DisplayName("Q10: encerramento antes do fim previsto")
    class Encerramento {

        @ParameterizedTest(name = "encerrar às {0} → fim {1}")
        @CsvSource({
            "2026-12-01T13:37:42Z, 2026-12-01T13:38:00Z",  // durante: arredonda para o próximo minuto cheio
            "2026-12-01T13:37:00Z, 2026-12-01T13:37:00Z",  // durante, já em minuto cheio: mantém
            "2026-12-01T14:59:01Z, 2026-12-01T15:00:00Z"   // no último minuto: arredondado = fim original
        })
        @DisplayName("durante o bloqueio: encurta até o próximo minuto cheio (Q3) e continua ocupando até lá")
        void encerraDurante(String agora, String fimEsperado) {
            var bloqueio = manutencao();

            bloqueio.encerrar(adm, Instant.parse(agora));

            var esperado = new PeriodoReserva(INICIO, Instant.parse(fimEsperado));
            assertThat(bloqueio.getPeriodo()).isEqualTo(esperado);
            assertThat(bloqueio.getOcupacoes()).singleElement().satisfies(o -> {
                assertThat(o.getPeriodo()).isEqualTo(esperado);
                assertThat(o.isBloqueante()).isTrue();
            });
        }

        @ParameterizedTest(name = "encerrar às {0} → bloqueio desfeito")
        @ValueSource(strings = {"2026-12-01T10:00:00Z", "2026-12-01T13:00:00Z"})
        @DisplayName("antes do início (ou exatamente no início): o bloqueio inteiro é desfeito e o horário fica livre")
        void encerraAntesDeComecar(String agora) {
            var bloqueio = manutencao();

            bloqueio.encerrar(adm, Instant.parse(agora));

            assertThat(bloqueio.isAtivo()).isFalse();
            assertThat(bloqueio.getOcupacoes()).allSatisfy(o -> assertThat(o.isBloqueante()).isFalse());
        }

        @ParameterizedTest(name = "encerrar às {0} → recusado")
        @ValueSource(strings = {"2026-12-01T15:00:00Z", "2026-12-01T16:00:00Z"})
        @DisplayName("no fim ou depois: recusado, o bloqueio já terminou")
        void encerraDepoisDoFim(String agora) {
            var bloqueio = manutencao();

            assertThatThrownBy(() -> bloqueio.encerrar(adm, Instant.parse(agora)))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Este bloqueio já terminou.");
        }

        @Test
        @DisplayName("encerrar duas vezes: a segunda é recusada")
        void encerraDuasVezes() {
            var bloqueio = manutencao();
            bloqueio.encerrar(adm, ANTES);

            assertThatThrownBy(() -> bloqueio.encerrar(adm, ANTES))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Este bloqueio já foi encerrado.");
        }

        @Test
        @DisplayName("só ADMINISTRADOR encerra")
        void soAdministradorEncerra() {
            var bloqueio = manutencao();
            var responsavel = new Usuario("rita@senac.br", Perfil.RESPONSAVEL);

            assertThatThrownBy(() -> bloqueio.encerrar(responsavel, ANTES))
                    .isInstanceOf(OperacaoNaoPermitidaException.class);
            assertThat(bloqueio.isAtivo()).isTrue();
        }
    }
}
