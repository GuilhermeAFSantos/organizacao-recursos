package br.senac.qs.recursos.domain.model;

import static br.senac.qs.recursos.domain.model.StatusReserva.APROVADA;
import static br.senac.qs.recursos.domain.model.StatusReserva.SOLICITADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Alteração de horário (RF04). Reserva de 10:00 às 12:00 (Brasília) = 13:00Z às 15:00Z, movida
 * para 14:00 às 16:00 = 17:00Z às 19:00Z.
 */
@DisplayName("Alteração de horário da reserva")
class AlteracaoReservaTest {

    static final Instant INICIO = Instant.parse("2026-12-01T13:00:00Z");
    static final Instant FIM = Instant.parse("2026-12-01T15:00:00Z");
    static final Instant ANTES = Instant.parse("2026-12-01T10:00:00Z");
    static final PeriodoReserva NOVO = new PeriodoReserva(Instant.parse("2026-12-01T17:00:00Z"),
            Instant.parse("2026-12-01T19:00:00Z"));
    static final Duration UM_MINUTO = Duration.ofMinutes(1);

    final Usuario ana = new Usuario("ana@senac.br", Perfil.SOLICITANTE);
    final Usuario bruno = new Usuario("bruno@senac.br", Perfil.SOLICITANTE);
    final Usuario rita = new Usuario("rita@senac.br", Perfil.RESPONSAVEL);
    final Usuario adm = new Usuario("adm@senac.br", Perfil.ADMINISTRADOR);

    Reserva reservaDe(Sala sala) {
        var reserva = Reserva.solicitar(ana, List.of(sala), new PeriodoReserva(INICIO, FIM), "Aula", ANTES);
        reserva.retirarMudancas();
        return reserva;
    }

    Reserva salaComum() {
        return reservaDe(new Sala("Sala 101", false));
    }

    Reserva laboratorio() {
        return reservaDe(new Sala("Laboratório 3", true));
    }

    @Nested
    @DisplayName("Quando é permitida")
    class Permitida {

        @Test
        @DisplayName("a dona move a reserva: período e ocupações mudam juntos, e a alteração é auditada")
        void dona() {
            var reserva = laboratorio();

            reserva.alterarPeriodo(ana, NOVO, ANTES);

            assertThat(reserva.getPeriodo()).isEqualTo(NOVO);
            assertThat(reserva.getOcupacoes()).extracting(Ocupacao::getPeriodo).containsOnly(NOVO);
            assertThat(reserva.getStatus()).isEqualTo(SOLICITADA);
            assertThat(reserva.retirarMudancas()).singleElement().satisfies(m -> {
                assertThat(m.acao()).isEqualTo(MudancaDeEstado.ALTERACAO);
                assertThat(m.anterior()).isEqualTo(SOLICITADA);
                assertThat(m.novo()).isEqualTo(SOLICITADA);
                assertThat(m.autorEmail()).isEqualTo("ana@senac.br");
                assertThat(m.motivo())
                        .isEqualTo("Horário alterado de 01/12/2026 10:00–12:00 para 01/12/2026 14:00–16:00.");
            });
        }

        @Test
        @DisplayName("o ADMINISTRADOR também pode alterar")
        void administrador() {
            var reserva = salaComum();

            reserva.alterarPeriodo(adm, NOVO, ANTES);

            assertThat(reserva.getPeriodo()).isEqualTo(NOVO);
        }

        @Test
        @DisplayName("fronteira: 1 minuto antes do início ainda pode; o novo início 1 minuto no futuro também")
        void fronteiras() {
            var reserva = salaComum();
            var agora = INICIO.minus(UM_MINUTO);
            var logoDepois = new PeriodoReserva(agora.plus(UM_MINUTO), agora.plus(Duration.ofHours(1)));

            reserva.alterarPeriodo(ana, logoDepois, agora);

            assertThat(reserva.getPeriodo()).isEqualTo(logoDepois);
        }
    }

    @Nested
    @DisplayName("Reaprovação")
    class Reaprovacao {

        @Test
        @DisplayName("aprovada com recurso restrito volta a SOLICITADA: a aprovação valia para o horário antigo")
        void restritoVoltaParaSolicitada() {
            var reserva = laboratorio();
            reserva.aprovar(rita, ANTES);
            reserva.retirarMudancas();

            reserva.alterarPeriodo(ana, NOVO, ANTES);

            assertThat(reserva.getStatus()).isEqualTo(SOLICITADA);
            assertThat(reserva.getAprovador()).isNull();
            assertThat(reserva.getMotivoStatus()).isEqualTo("Horário alterado: a reserva precisa de nova aprovação.");
            assertThat(reserva.retirarMudancas()).singleElement().satisfies(m -> {
                assertThat(m.anterior()).isEqualTo(APROVADA);
                assertThat(m.novo()).isEqualTo(SOLICITADA);
            });
        }

        @Test
        @DisplayName("aprovada sem recurso restrito continua APROVADA (não há quem precise aprovar)")
        void semRestritoContinuaAprovada() {
            var reserva = salaComum();

            reserva.alterarPeriodo(ana, NOVO, ANTES);

            assertThat(reserva.getStatus()).isEqualTo(APROVADA);
        }
    }

    @Nested
    @DisplayName("Quando é recusada")
    class Recusada {

        @Test
        @DisplayName("outro solicitante ou um RESPONSAVEL não alteram (403)")
        void semPermissao() {
            var reserva = salaComum();
            var mensagem = "Somente quem solicitou a reserva ou um ADMINISTRADOR pode alterá-la.";

            assertThatThrownBy(() -> reserva.alterarPeriodo(bruno, NOVO, ANTES))
                    .isInstanceOf(OperacaoNaoPermitidaException.class).hasMessage(mensagem);
            assertThatThrownBy(() -> reserva.alterarPeriodo(rita, NOVO, ANTES))
                    .isInstanceOf(OperacaoNaoPermitidaException.class).hasMessage(mensagem);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = StatusReserva.class, names = {"EM_USO", "CONCLUIDA", "REJEITADA", "CANCELADA",
                "NAO_COMPARECEU"})
        @DisplayName("só reservas solicitadas ou aprovadas mudam de horário")
        void estadosFinaisOuEmUso(StatusReserva estado) {
            var reserva = laboratorio();
            levarPara(reserva, estado);

            assertThatThrownBy(() -> reserva.alterarPeriodo(ana, NOVO, ANTES))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Só é possível alterar o horário de reservas solicitadas ou aprovadas.");
        }

        @Test
        @DisplayName("fronteira: no horário de início já não pode")
        void noInicio() {
            var reserva = salaComum();

            assertThatThrownBy(() -> reserva.alterarPeriodo(ana, NOVO, INICIO))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("O horário só pode ser alterado antes do início da reserva.");
        }

        @Test
        @DisplayName("fronteira: o novo horário não pode começar agora nem no passado")
        void novoNoPassado() {
            var reserva = salaComum();
            var agora = Instant.parse("2026-12-01T11:00:00Z");
            var comecandoAgora = new PeriodoReserva(agora, agora.plus(Duration.ofHours(1)));

            assertThatThrownBy(() -> reserva.alterarPeriodo(ana, comecandoAgora, agora))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("O novo horário deve começar no futuro.");
        }

        @Test
        @DisplayName("a permissão é verificada antes do estado (não revela o estado a quem não pode agir)")
        void permissaoPrimeiro() {
            var reserva = salaComum();
            reserva.cancelar(ana, null, ANTES);

            assertThatThrownBy(() -> reserva.alterarPeriodo(bruno, NOVO, ANTES))
                    .isInstanceOf(OperacaoNaoPermitidaException.class);
        }

        @Test
        @DisplayName("recusa não muda nada")
        void recusaNaoMuda() {
            var reserva = salaComum();

            assertThatThrownBy(() -> reserva.alterarPeriodo(ana, NOVO, INICIO))
                    .isInstanceOf(RegraNegocioException.class);

            assertThat(reserva.getPeriodo()).isEqualTo(new PeriodoReserva(INICIO, FIM));
            assertThat(reserva.getOcupacoes()).extracting(Ocupacao::getPeriodo)
                    .containsOnly(new PeriodoReserva(INICIO, FIM));
            assertThat(reserva.retirarMudancas()).isEmpty();
        }

        void levarPara(Reserva reserva, StatusReserva estado) {
            switch (estado) {
                case REJEITADA -> reserva.rejeitar(rita, "Sem vaga", ANTES);
                case CANCELADA -> reserva.cancelar(ana, null, ANTES);
                case EM_USO -> {
                    reserva.aprovar(rita, ANTES);
                    reserva.iniciarUso(rita, INICIO);
                }
                case CONCLUIDA -> {
                    reserva.aprovar(rita, ANTES);
                    reserva.iniciarUso(rita, INICIO);
                    reserva.concluir(rita, FIM);
                }
                case NAO_COMPARECEU -> {
                    reserva.aprovar(rita, ANTES);
                    reserva.registrarNaoComparecimento(rita, null, INICIO);
                }
                default -> throw new IllegalArgumentException(estado.name());
            }
            assertThat(reserva.getStatus()).isEqualTo(estado);
        }
    }
}
