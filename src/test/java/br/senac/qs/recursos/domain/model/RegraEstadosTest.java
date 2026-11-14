package br.senac.qs.recursos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
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

    /** Leva a reserva ao estado pedido pelos caminhos permitidos e descarta as mudanças até ali. */
    Reserva reservaNoEstado(StatusReserva estado) {
        var reserva = Reserva.solicitar(ana, List.of(laboratorio), new PeriodoReserva(INICIO, FIM), "Aula", ANTES);
        if (estado != StatusReserva.SOLICITADA) {
            reserva.aprovar(rita, ANTES);
        }
        if (estado == StatusReserva.EM_USO) {
            reserva.iniciarUso(rita, INICIO);
        }
        reserva.retirarMudancas();
        return reserva;
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
