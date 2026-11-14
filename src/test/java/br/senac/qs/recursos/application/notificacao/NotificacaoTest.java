package br.senac.qs.recursos.application.notificacao;

import static br.senac.qs.recursos.domain.model.StatusReserva.APROVADA;
import static br.senac.qs.recursos.domain.model.StatusReserva.SOLICITADA;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import br.senac.qs.recursos.domain.model.MudancaDeEstado;
import br.senac.qs.recursos.domain.model.PeriodoReserva;
import br.senac.qs.recursos.domain.model.StatusReserva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Conteúdo da notificação enviada ao solicitante (RF11) a cada mudança na reserva. Reserva de
 * 01/12/2026 10:00–12:00 (Brasília).
 */
@DisplayName("Notificação ao solicitante")
class NotificacaoTest {

    static final PeriodoReserva PERIODO = new PeriodoReserva(Instant.parse("2026-12-01T13:00:00Z"),
            Instant.parse("2026-12-01T15:00:00Z"));
    static final Instant QUANDO = Instant.parse("2026-11-20T12:00:00Z");

    static ReservaMudou evento(MudancaDeEstado mudanca) {
        return new ReservaMudou(42L, "ana@senac.br", PERIODO, mudanca);
    }

    static MudancaDeEstado transicao(StatusReserva anterior, StatusReserva novo, String motivo) {
        return new MudancaDeEstado(anterior, novo, 7L, "rita@senac.br", motivo, QUANDO);
    }

    @ParameterizedTest(name = "{0} → {1}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
        APROVADA  | EM_USO         | RESERVA_EM_USO         | O uso da sua reserva de 01/12/2026 10:00–12:00 foi iniciado.
        EM_USO    | CONCLUIDA      | RESERVA_CONCLUIDA      | Sua reserva de 01/12/2026 10:00–12:00 foi concluída.
        SOLICITADA| APROVADA       | RESERVA_APROVADA       | Sua reserva de 01/12/2026 10:00–12:00 foi aprovada.
        APROVADA  | CANCELADA      | RESERVA_CANCELADA      | Sua reserva de 01/12/2026 10:00–12:00 foi cancelada.
        APROVADA  | NAO_COMPARECEU | RESERVA_NAO_COMPARECEU | Foi registrado o não comparecimento na sua reserva de 01/12/2026 10:00–12:00.
        """)
    @DisplayName("cada estado tem evento e mensagem próprios, com o horário local")
    void porEstado(StatusReserva anterior, StatusReserva novo, String evento, String mensagem) {
        var notificacao = Notificacao.de(evento(transicao(anterior, novo, null)));

        assertThat(notificacao.evento()).isEqualTo(evento);
        assertThat(notificacao.mensagem()).isEqualTo(mensagem);
        assertThat(notificacao.reservaId()).isEqualTo(42L);
        assertThat(notificacao.destinatario()).isEqualTo("ana@senac.br");
        assertThat(notificacao.ocorridoEm()).isEqualTo(QUANDO);
    }

    @Test
    @DisplayName("criação que aguarda aprovação")
    void criada() {
        var mudanca = new MudancaDeEstado(null, SOLICITADA, 1L, "ana@senac.br", null, QUANDO);

        assertThat(Notificacao.de(evento(mudanca)).mensagem())
                .isEqualTo("Sua reserva de 01/12/2026 10:00–12:00 foi registrada e aguarda aprovação.");
    }

    @Test
    @DisplayName("aprovação automática diz o porquê")
    void aprovacaoAutomatica() {
        var mudanca = new MudancaDeEstado(SOLICITADA, APROVADA, null, MudancaDeEstado.AUTOR_SISTEMA,
                "Nenhum recurso é restrito: aprovação automática.", QUANDO);

        assertThat(Notificacao.de(evento(mudanca)).mensagem())
                .isEqualTo("Sua reserva de 01/12/2026 10:00–12:00 foi aprovada automaticamente: nenhum recurso é restrito.");
    }

    @Test
    @DisplayName("rejeição e cancelamento informam o motivo, quando houver")
    void motivo() {
        assertThat(Notificacao.de(evento(transicao(SOLICITADA, StatusReserva.REJEITADA, "Sala em reforma")))
                .mensagem()).isEqualTo("Sua reserva de 01/12/2026 10:00–12:00 foi rejeitada. Motivo: Sala em reforma");
        assertThat(Notificacao.de(evento(transicao(APROVADA, StatusReserva.CANCELADA, "Feriado"))).mensagem())
                .isEqualTo("Sua reserva de 01/12/2026 10:00–12:00 foi cancelada. Motivo: Feriado");
    }

    @Test
    @DisplayName("alteração de horário: evento próprio e aviso de nova aprovação quando necessária")
    void alteracao() {
        var motivo = "Horário alterado de 01/12/2026 08:00–09:00 para 01/12/2026 10:00–12:00.";
        var semReaprovacao = new MudancaDeEstado(MudancaDeEstado.ALTERACAO, APROVADA, APROVADA, 1L, "ana@senac.br",
                motivo, QUANDO);
        var comReaprovacao = new MudancaDeEstado(MudancaDeEstado.ALTERACAO, APROVADA, SOLICITADA, 1L,
                "ana@senac.br", motivo, QUANDO);

        assertThat(Notificacao.de(evento(semReaprovacao)).evento()).isEqualTo("RESERVA_HORARIO_ALTERADO");
        assertThat(Notificacao.de(evento(semReaprovacao)).mensagem()).isEqualTo("Sua reserva foi alterada. " + motivo);
        assertThat(Notificacao.de(evento(comReaprovacao)).mensagem())
                .isEqualTo("Sua reserva foi alterada. " + motivo + " Ela precisa de nova aprovação.");
    }
}
