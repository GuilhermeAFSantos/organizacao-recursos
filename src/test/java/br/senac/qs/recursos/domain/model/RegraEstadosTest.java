package br.senac.qs.recursos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import br.senac.qs.recursos.domain.exception.TransicaoInvalidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Regra 4 de docs/testes/bdd-aprovacao-bloqueio.md (máquina de estados), refeita em TDD estrito.
 * Reserva do Laboratório 3 das 08:00 às 10:00 de 01/12/2026 (Brasília = 11:00Z às 13:00Z).
 */
@DisplayName("Regra 4: transições de estado da reserva")
class RegraEstadosTest {

    static final Instant ANTES = Instant.parse("2026-12-01T10:00:00Z");
    static final Instant INICIO = Instant.parse("2026-12-01T11:00:00Z");
    static final Instant FIM = Instant.parse("2026-12-01T13:00:00Z");

    final Usuario ana = new Usuario("ana@senac.br", Perfil.SOLICITANTE);
    final Usuario bruno = new Usuario("bruno@senac.br", Perfil.SOLICITANTE);
    final Usuario rita = new Usuario("rita@senac.br", Perfil.RESPONSAVEL);
    final Usuario adm = new Usuario("adm@senac.br", Perfil.ADMINISTRADOR);
    final Sala laboratorio = new Sala("Laboratório 3", true);

    @ParameterizedTest(name = "{0} --{1} por {2}--> {5} (horário {6})")
    @CsvSource({
        "SOLICITADA, aprovar,       rita, ANTES,  ,                     APROVADA,       ocupado",
        "SOLICITADA, rejeitar,      rita, ANTES,  sala em prova,        REJEITADA,      liberado",
        "SOLICITADA, cancelar,      ana,  ANTES,  aula remarcada,       CANCELADA,      liberado",
        "APROVADA,   cancelar,      ana,  ANTES,  aula remarcada,       CANCELADA,      liberado",
        "APROVADA,   cancelar,      adm,  ANTES,  evento institucional, CANCELADA,      liberado",
        "APROVADA,   iniciarUso,    rita, INICIO, ,                     EM_USO,         ocupado",
        "APROVADA,   naoCompareceu, rita, INICIO, ninguém compareceu,   NAO_COMPARECEU, liberado",
        "EM_USO,     concluir,      rita, FIM,    ,                     CONCLUIDA,      ocupado"
    })
    @DisplayName("ES-01: transição permitida muda o estado, registra 1 mudança com autor e motivo, e libera ou mantém o horário")
    void transicaoPermitidaMudaOEstadoERegistraAMudanca(StatusReserva de, String acao, String ator, String quando,
                                                         String motivo, StatusReserva para, String horario) {
        var reserva = reservaNoEstado(de);
        var autor = usuario(ator);
        var agora = instante(quando);

        executar(reserva, acao, autor, motivo, agora);

        assertThat(reserva.getStatus()).isEqualTo(para);
        assertThat(reserva.retirarMudancas()).singleElement().satisfies(m -> {
            assertThat(m.anterior()).isEqualTo(de);
            assertThat(m.novo()).isEqualTo(para);
            assertThat(m.autorEmail()).isEqualTo(autor.getEmail());
            assertThat(m.motivo()).isEqualTo(motivo);
            assertThat(m.quando()).isEqualTo(agora);
        });
        assertThat(reserva.getOcupacoes()).allSatisfy(o -> assertThat(o.isBloqueante()).isEqualTo(horario.equals("ocupado")));
    }

    @ParameterizedTest(name = "{0} --{1}--> {2} é recusada")
    @CsvSource({
        "SOLICITADA,     iniciarUso, EM_USO",
        "SOLICITADA,     concluir,   CONCLUIDA",
        "APROVADA,       aprovar,    APROVADA",
        "EM_USO,         cancelar,   CANCELADA",
        "CONCLUIDA,      aprovar,    APROVADA",
        "REJEITADA,      aprovar,    APROVADA",
        "CANCELADA,      cancelar,   CANCELADA",
        "NAO_COMPARECEU, iniciarUso, EM_USO"
    })
    @DisplayName("ES-02: transição não permitida é recusada, a reserva fica como estava e nenhuma mudança é registrada")
    void transicaoNaoPermitidaERecusadaSemRegistrarMudanca(StatusReserva de, String acao, StatusReserva para) {
        var reserva = reservaNoEstado(de);
        var autor = acao.equals("cancelar") ? ana : rita;

        assertThatThrownBy(() -> executar(reserva, acao, autor, "motivo", ANTES))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessageStartingWith("Não é possível mudar a reserva de " + de + " para " + para + ".");

        assertThat(reserva.getStatus()).isEqualTo(de);
        assertThat(reserva.retirarMudancas()).isEmpty();
    }

    @Test
    @DisplayName("ES-03: outro solicitante não cancela reserva alheia, que continua APROVADA")
    void outroSolicitanteNaoCancelaReservaAlheia() {
        var reserva = reservaNoEstado(StatusReserva.APROVADA);

        assertThatThrownBy(() -> reserva.cancelar(bruno, null, ANTES)).isInstanceOf(OperacaoNaoPermitidaException.class)
                .hasMessage("Somente quem solicitou a reserva ou um ADMINISTRADOR pode cancelá-la.");

        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.APROVADA);
    }

    @ParameterizedTest(name = "agora {0} (Brasília {1}) → cancelada = {2}")
    @CsvSource({
        "2026-12-01T10:59:59Z, 07:59:59, true",
        "2026-12-01T11:00:00Z, 08:00:00, false",
        "2026-12-01T11:30:00Z, 08:30:00, false"
    })
    @DisplayName("ES-04: reserva APROVADA só é cancelada antes do início, mesmo sem início de uso registrado")
    void reservaAprovadaSoECanceladaAntesDoInicio(String agora, String horaLocal, boolean cancelada) {
        var relogio = Clock.fixed(Instant.parse(agora), ZoneOffset.UTC);
        var reserva = reservaNoEstado(StatusReserva.APROVADA);

        if (cancelada) {
            reserva.cancelar(ana, null, relogio.instant());
            assertThat(reserva.getStatus()).isEqualTo(StatusReserva.CANCELADA);
        } else {
            assertThatThrownBy(() -> reserva.cancelar(ana, null, relogio.instant()))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Uma reserva aprovada só pode ser cancelada antes do horário de início.");
            assertThat(reserva.getStatus()).isEqualTo(StatusReserva.APROVADA);
        }
    }

    /** Leva a reserva ao estado pedido pelos caminhos permitidos e descarta as mudanças até ali. */
    Reserva reservaNoEstado(StatusReserva estado) {
        var reserva = Reserva.solicitar(ana, List.of(laboratorio), new PeriodoReserva(INICIO, FIM), "Aula", ANTES);
        switch (estado) {
            case SOLICITADA -> { }
            case REJEITADA -> reserva.rejeitar(rita, "sem vaga", ANTES);
            case CANCELADA -> reserva.cancelar(ana, null, ANTES);
            case APROVADA -> reserva.aprovar(rita, ANTES);
            case EM_USO -> emUso(reserva);
            case CONCLUIDA -> {
                emUso(reserva);
                reserva.concluir(rita, FIM);
            }
            case NAO_COMPARECEU -> {
                reserva.aprovar(rita, ANTES);
                reserva.registrarNaoComparecimento(rita, null, INICIO);
            }
        }
        reserva.retirarMudancas();
        return reserva;
    }

    void emUso(Reserva reserva) {
        reserva.aprovar(rita, ANTES);
        reserva.iniciarUso(rita, INICIO);
    }

    static void executar(Reserva reserva, String acao, Usuario autor, String motivo, Instant agora) {
        switch (acao) {
            case "aprovar" -> reserva.aprovar(autor, agora);
            case "rejeitar" -> reserva.rejeitar(autor, motivo, agora);
            case "cancelar" -> reserva.cancelar(autor, motivo, agora);
            case "iniciarUso" -> reserva.iniciarUso(autor, agora);
            case "naoCompareceu" -> reserva.registrarNaoComparecimento(autor, motivo, agora);
            case "concluir" -> reserva.concluir(autor, agora);
            default -> throw new IllegalArgumentException(acao);
        }
    }

    Usuario usuario(String nome) {
        return switch (nome) {
            case "ana" -> ana;
            case "rita" -> rita;
            case "adm" -> adm;
            default -> throw new IllegalArgumentException(nome);
        };
    }

    static Instant instante(String nome) {
        return switch (nome) {
            case "ANTES" -> ANTES;
            case "INICIO" -> INICIO;
            case "FIM" -> FIM;
            default -> throw new IllegalArgumentException(nome);
        };
    }
}
