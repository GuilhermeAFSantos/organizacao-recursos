package br.senac.qs.recursos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regras 1 e 2 de docs/testes/bdd-aprovacao-bloqueio.md, refeitas em TDD estrito: um cenário por
 * vez (evidência em docs/testes/evidencia-tdd.md). Reserva das 08:00 às 10:00 de 01/12/2026 (Brasília).
 */
@DisplayName("Regras 1 e 2: aprovação de recursos restritos")
class RegraAprovacaoTest {

    static final Instant INICIO = Instant.parse("2026-12-01T11:00:00Z");
    static final Instant FIM = Instant.parse("2026-12-01T13:00:00Z");
    static final Instant AGORA = Instant.parse("2026-12-01T10:00:00Z");
    static final PeriodoReserva PERIODO = new PeriodoReserva(INICIO, FIM);

    final Usuario ana = new Usuario("ana@senac.br", Perfil.SOLICITANTE);
    final Sala sala101 = new Sala("Sala 101", false);

    @Test
    @DisplayName("AP-01: reserva só com recursos não restritos nasce APROVADA pelo sistema")
    void reservaSoComRecursosNaoRestritosNasceAprovadaPeloSistema() {
        var reserva = Reserva.solicitar(ana, List.of(sala101), PERIODO, "Aula", AGORA);

        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.APROVADA);
        assertThat(reserva.getAprovador()).isNull();
        assertThat(reserva.retirarMudancas()).extracting(MudancaDeEstado::novo, MudancaDeEstado::autorEmail)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(StatusReserva.SOLICITADA, "ana@senac.br"),
                        org.assertj.core.groups.Tuple.tuple(StatusReserva.APROVADA, MudancaDeEstado.AUTOR_SISTEMA));
    }
}
