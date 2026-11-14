package br.senac.qs.recursos.domain.model;

import static br.senac.qs.recursos.domain.model.StatusReserva.APROVADA;
import static br.senac.qs.recursos.domain.model.StatusReserva.CANCELADA;
import static br.senac.qs.recursos.domain.model.StatusReserva.CONCLUIDA;
import static br.senac.qs.recursos.domain.model.StatusReserva.EM_USO;
import static br.senac.qs.recursos.domain.model.StatusReserva.NAO_COMPARECEU;
import static br.senac.qs.recursos.domain.model.StatusReserva.REJEITADA;
import static br.senac.qs.recursos.domain.model.StatusReserva.SOLICITADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import br.senac.qs.recursos.domain.exception.TransicaoInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Regras de transição da reserva (RC05, RC06, RC07, RC08), sem Spring e com o "agora" explícito.
 * Reserva do Laboratório 3 (restrito) de 08:00 às 10:00 (Brasília) = 11:00Z às 13:00Z.
 * Cenários: docs/testes/bdd-aprovacao-bloqueio.md.
 */
@DisplayName("Reserva: transições de estado")
class ReservaTest {

    static final Instant INICIO = Instant.parse("2026-12-01T11:00:00Z");
    static final Instant FIM = Instant.parse("2026-12-01T13:00:00Z");
    static final Instant ANTES = INICIO.minus(Duration.ofHours(4));
    static final Instant DURANTE = INICIO.plus(Duration.ofMinutes(30));

    final Usuario ana = new Usuario("ana@senac.br", Perfil.SOLICITANTE);
    final Usuario bruno = new Usuario("bruno@senac.br", Perfil.SOLICITANTE);
    final Usuario rita = new Usuario("rita@senac.br", Perfil.RESPONSAVEL);
    final Usuario raul = new Usuario("raul@senac.br", Perfil.RESPONSAVEL);
    final Usuario adm = new Usuario("adm@senac.br", Perfil.ADMINISTRADOR);
    final Sala laboratorio = new Sala("Laboratório 3", true);

    Reserva reserva;

    @BeforeEach
    void reservaSolicitadaPorAna() {
        reserva = Reserva.solicitar(ana, laboratorio, new PeriodoReserva(INICIO, FIM), "Aula", ANTES);
    }

    @Test
    @DisplayName("RC08: a criação registra a mudança [nenhum] → SOLICITADA, feita pela solicitante")
    void criacaoRegistraMudanca() {
        assertThat(reserva.retirarMudancas()).singleElement().satisfies(m -> {
            assertThat(m.anterior()).isNull();
            assertThat(m.novo()).isEqualTo(SOLICITADA);
            assertThat(m.autorEmail()).isEqualTo("ana@senac.br");
            assertThat(m.quando()).isEqualTo(ANTES);
        });
        assertThat(reserva.retirarMudancas()).as("retirar esvazia a lista").isEmpty();
    }

    @Nested
    @DisplayName("Aprovação (RF07, RC06)")
    class Aprovacao {

        @Test
        @DisplayName("AP-04: RESPONSAVEL aprova; aprovador e auditoria registrados")
        void responsavelAprova() {
            reserva.retirarMudancas();

            reserva.aprovar(rita, ANTES);

            assertThat(reserva.getStatus()).isEqualTo(APROVADA);
            assertThat(reserva.getAprovador()).isSameAs(rita);
            assertThat(reserva.retirarMudancas()).singleElement()
                    .isEqualTo(new MudancaDeEstado(SOLICITADA, APROVADA, null, "rita@senac.br", null, ANTES));
        }

        @ParameterizedTest(name = "perfil {0} não aprova")
        @ValueSource(strings = {"bruno", "ana", "adm"})
        @DisplayName("AP-05/AU-01: só o perfil RESPONSAVEL aprova (inclusive a dona da reserva não aprova)")
        void soResponsavelAprova(String quem) {
            Usuario autor = switch (quem) {
                case "bruno" -> bruno;
                case "ana" -> ana;
                default -> adm;
            };

            assertThatThrownBy(() -> reserva.aprovar(autor, ANTES))
                    .isInstanceOf(OperacaoNaoPermitidaException.class)
                    .hasMessage("Somente um RESPONSAVEL pode aprovar reservas.");
            assertThat(reserva.getStatus()).isEqualTo(SOLICITADA);
        }

        @Test
        @DisplayName("AU-04: RESPONSAVEL não aprova a reserva que ele mesmo solicitou; outro RESPONSAVEL aprova")
        void naoSeAutoaprova() {
            var daRita = Reserva.solicitar(rita, laboratorio, new PeriodoReserva(INICIO, FIM), "Reunião", ANTES);

            assertThatThrownBy(() -> daRita.aprovar(rita, ANTES))
                    .isInstanceOf(OperacaoNaoPermitidaException.class)
                    .hasMessage("Você não pode aprovar ou rejeitar uma reserva solicitada por você.");

            daRita.aprovar(raul, ANTES);
            assertThat(daRita.getAprovador()).isSameAs(raul);
        }

        @ParameterizedTest(name = "aprovar {0} s em relação ao início → permitido={1}")
        @CsvSource({"-1, true", "0, false", "1800, false"})
        @DisplayName("Q8: aprovar só antes do início (1 s antes pode; no início ou depois, não)")
        void aprovarSoAntesDoInicio(long segundosDoInicio, boolean permitido) {
            Instant agora = INICIO.plusSeconds(segundosDoInicio);

            if (permitido) {
                reserva.aprovar(rita, agora);
                assertThat(reserva.getStatus()).isEqualTo(APROVADA);
            } else {
                assertThatThrownBy(() -> reserva.aprovar(rita, agora))
                        .isInstanceOf(RegraNegocioException.class)
                        .hasMessage("A reserva só pode ser aprovada antes do horário de início.");
                assertThat(reserva.getStatus()).isEqualTo(SOLICITADA);
            }
        }
    }

    @Nested
    @DisplayName("Rejeição")
    class Rejeicao {

        @Test
        @DisplayName("AP-06: RESPONSAVEL rejeita com motivo; o horário é liberado")
        void rejeitaComMotivo() {
            reserva.retirarMudancas();

            reserva.rejeitar(rita, "Laboratório reservado para prova", ANTES);

            assertThat(reserva.getStatus()).isEqualTo(REJEITADA);
            assertThat(reserva.getMotivoStatus()).isEqualTo("Laboratório reservado para prova");
            assertThat(reserva.getOcupacoes()).allSatisfy(o -> assertThat(o.isBloqueante()).isFalse());
            assertThat(reserva.retirarMudancas()).singleElement()
                    .extracting(MudancaDeEstado::motivo).isEqualTo("Laboratório reservado para prova");
        }

        @ParameterizedTest(name = "motivo [{0}] é recusado")
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        @DisplayName("AP-07: rejeição exige motivo")
        void exigeMotivo(String motivo) {
            assertThatThrownBy(() -> reserva.rejeitar(rita, motivo, ANTES))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Informe o motivo da rejeição.");
            assertThat(reserva.getStatus()).isEqualTo(SOLICITADA);
        }

        @Test
        @DisplayName("Q8: depois do início, a solicitação pendente ainda pode ser rejeitada")
        void rejeitaDepoisDoInicio() {
            reserva.rejeitar(rita, "Não analisada a tempo", DURANTE);

            assertThat(reserva.getStatus()).isEqualTo(REJEITADA);
        }
    }

    @Nested
    @DisplayName("Cancelamento (Q7, RC07)")
    class Cancelamento {

        @Test
        @DisplayName("a dona cancela antes do início; o horário é liberado")
        void donaCancela() {
            reserva.aprovar(rita, ANTES);

            reserva.cancelar(ana, "Aula remarcada", ANTES);

            assertThat(reserva.getStatus()).isEqualTo(CANCELADA);
            assertThat(reserva.getOcupacoes()).allSatisfy(o -> assertThat(o.isBloqueante()).isFalse());
        }

        @Test
        @DisplayName("ADMINISTRADOR cancela reserva de qualquer pessoa")
        void administradorCancela() {
            reserva.cancelar(adm, "Evento institucional", ANTES);

            assertThat(reserva.getStatus()).isEqualTo(CANCELADA);
        }

        @Test
        @DisplayName("ES-03: outro solicitante não cancela reserva alheia")
        void outroNaoCancela() {
            assertThatThrownBy(() -> reserva.cancelar(bruno, null, ANTES))
                    .isInstanceOf(OperacaoNaoPermitidaException.class)
                    .hasMessage("Somente quem solicitou a reserva ou um ADMINISTRADOR pode cancelá-la.");
        }

        @ParameterizedTest(name = "cancelar APROVADA {0} s em relação ao início → permitido={1}")
        @CsvSource({"-1, true", "0, false", "1800, false"})
        @DisplayName("ES-04/Q7: reserva APROVADA só é cancelada antes do início")
        void aprovadaSoAntesDoInicio(long segundosDoInicio, boolean permitido) {
            reserva.aprovar(rita, ANTES);
            Instant agora = INICIO.plusSeconds(segundosDoInicio);

            if (permitido) {
                reserva.cancelar(ana, null, agora);
                assertThat(reserva.getStatus()).isEqualTo(CANCELADA);
            } else {
                assertThatThrownBy(() -> reserva.cancelar(ana, null, agora))
                        .isInstanceOf(RegraNegocioException.class)
                        .hasMessage("Uma reserva aprovada só pode ser cancelada antes do horário de início.");
                assertThat(reserva.getStatus()).isEqualTo(APROVADA);
            }
        }

        @Test
        @DisplayName("Q8: solicitação ainda pendente pode ser cancelada mesmo depois do início")
        void solicitadaDepoisDoInicio() {
            reserva.cancelar(ana, null, DURANTE);

            assertThat(reserva.getStatus()).isEqualTo(CANCELADA);
        }

        @Test
        @DisplayName("RC07: reserva EM_USO não pode ser cancelada")
        void emUsoNaoCancela() {
            reserva.aprovar(rita, ANTES);
            reserva.iniciarUso(rita, INICIO);

            assertThatThrownBy(() -> reserva.cancelar(ana, null, INICIO))
                    .isInstanceOf(TransicaoInvalidaException.class);
            assertThat(reserva.getStatus()).isEqualTo(EM_USO);
        }
    }

    @Nested
    @DisplayName("Uso, não comparecimento e conclusão")
    class UsoEConclusao {

        @Test
        @DisplayName("ES-01: fluxo principal SOLICITADA → APROVADA → EM_USO → CONCLUIDA, com 4 mudanças auditadas")
        void fluxoPrincipal() {
            reserva.aprovar(rita, ANTES);
            reserva.iniciarUso(rita, INICIO);
            reserva.concluir(rita, FIM);

            assertThat(reserva.getStatus()).isEqualTo(CONCLUIDA);
            assertThat(reserva.retirarMudancas()).extracting(MudancaDeEstado::novo)
                    .containsExactly(SOLICITADA, APROVADA, EM_USO, CONCLUIDA);
            assertThat(reserva.getOcupacoes()).as("CONCLUIDA mantém o horário ocupado")
                    .allSatisfy(o -> assertThat(o.isBloqueante()).isTrue());
        }

        @ParameterizedTest(name = "não comparecimento {0} s em relação ao início → permitido={1}")
        @CsvSource({"-1, false", "0, true", "1800, true"})
        @DisplayName("Q5: não comparecimento só a partir do início; libera o horário")
        void naoComparecimento(long segundosDoInicio, boolean permitido) {
            reserva.aprovar(rita, ANTES);
            Instant agora = INICIO.plusSeconds(segundosDoInicio);

            if (permitido) {
                reserva.registrarNaoComparecimento(rita, "Ninguém compareceu", agora);
                assertThat(reserva.getStatus()).isEqualTo(NAO_COMPARECEU);
                assertThat(reserva.getOcupacoes()).allSatisfy(o -> assertThat(o.isBloqueante()).isFalse());
            } else {
                assertThatThrownBy(() -> reserva.registrarNaoComparecimento(rita, null, agora))
                        .isInstanceOf(RegraNegocioException.class)
                        .hasMessage("O não comparecimento só pode ser registrado a partir do horário de início.");
            }
        }

        @Test
        @DisplayName("só RESPONSAVEL registra início de uso")
        void soResponsavelIniciaUso() {
            reserva.aprovar(rita, ANTES);

            assertThatThrownBy(() -> reserva.iniciarUso(ana, INICIO))
                    .isInstanceOf(OperacaoNaoPermitidaException.class)
                    .hasMessage("Somente um RESPONSAVEL pode registrar o início de uso de reservas.");
        }
    }

    @Nested
    @DisplayName("ES-02: transições não permitidas")
    class TransicoesInvalidas {

        @Test
        @DisplayName("pular a aprovação (SOLICITADA → EM_USO) é recusado com mensagem que lista as opções")
        void pularAprovacao() {
            reserva.retirarMudancas();

            assertThatThrownBy(() -> reserva.iniciarUso(rita, INICIO))
                    .isInstanceOf(TransicaoInvalidaException.class)
                    .hasMessageStartingWith("Não é possível mudar a reserva de SOLICITADA para EM_USO.");
            assertThat(reserva.getStatus()).isEqualTo(SOLICITADA);
            assertThat(reserva.retirarMudancas()).as("recusa não gera auditoria").isEmpty();
        }

        @Test
        @DisplayName("estado final não tem saída: CONCLUIDA não volta a APROVADA")
        void estadoFinal() {
            reserva.aprovar(rita, ANTES);
            reserva.iniciarUso(rita, INICIO);
            reserva.concluir(rita, FIM);

            assertThatThrownBy(() -> reserva.aprovar(rita, ANTES))
                    .isInstanceOf(TransicaoInvalidaException.class)
                    .hasMessage("Não é possível mudar a reserva de CONCLUIDA para APROVADA. "
                            + "CONCLUIDA é um estado final.");
        }
    }
}
