package br.senac.qs.recursos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    final Sala laboratorio = new Sala("Laboratório 3", true);
    final Usuario rita = new Usuario("rita@senac.br", Perfil.RESPONSAVEL);
    final Usuario raul = new Usuario("raul@senac.br", Perfil.RESPONSAVEL);
    final Usuario bruno = new Usuario("bruno@senac.br", Perfil.SOLICITANTE);
    final Usuario adm = new Usuario("adm@senac.br", Perfil.ADMINISTRADOR);
    final Professor carlos = Professor.cadastrar(adm, "Prof. Carlos", "P-1", null, java.util.Set.of());

    /** Reserva pedida pela ana, no período padrão, com os recursos informados. */
    Reserva solicitar(Recurso... recursos) {
        return Reserva.solicitar(ana, List.of(recursos), PERIODO, "Aula", AGORA);
    }

    @Test
    @DisplayName("AP-01: reserva só com recursos não restritos nasce APROVADA pelo sistema")
    void reservaSoComRecursosNaoRestritosNasceAprovadaPeloSistema() {
        var reserva = solicitar(sala101);

        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.APROVADA);
        assertThat(reserva.getAprovador()).isNull();
        assertThat(reserva.retirarMudancas()).extracting(MudancaDeEstado::novo, MudancaDeEstado::autorEmail)
                .containsExactly(
                        tuple(StatusReserva.SOLICITADA, "ana@senac.br"),
                        tuple(StatusReserva.APROVADA, MudancaDeEstado.AUTOR_SISTEMA));
    }

    @Test
    @DisplayName("AP-02: reserva com recurso restrito fica SOLICITADA e já segura o horário")
    void reservaComRecursoRestritoFicaSolicitadaESeguraOHorario() {
        var reserva = solicitar(laboratorio);

        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.SOLICITADA);
        assertThat(reserva.getOcupacoes()).singleElement().satisfies(o -> assertThat(o.isBloqueante()).isTrue());
    }

    @Test
    @DisplayName("AP-03: basta um recurso restrito (o professor, depois da sala comum) para a reserva aguardar aprovação")
    void bastaUmRecursoRestritoNaListaParaAReservaAguardarAprovacao() {
        var reserva = solicitar(sala101, carlos);

        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.SOLICITADA);
        assertThat(reserva.getOcupacoes()).hasSize(2).allSatisfy(o -> assertThat(o.isBloqueante()).isTrue());
    }

    @Test
    @DisplayName("AP-04: RESPONSAVEL aprova reserva de recurso restrito: APROVADA, com aprovador e mudança registrada")
    void responsavelAprovaReservaDeRecursoRestrito() {
        var reserva = solicitar(laboratorio);
        reserva.retirarMudancas();

        reserva.aprovar(rita, AGORA);

        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.APROVADA);
        assertThat(reserva.getAprovador()).isSameAs(rita);
        assertThat(reserva.retirarMudancas()).singleElement().satisfies(m -> {
            assertThat(m.anterior()).isEqualTo(StatusReserva.SOLICITADA);
            assertThat(m.novo()).isEqualTo(StatusReserva.APROVADA);
            assertThat(m.autorEmail()).isEqualTo("rita@senac.br");
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"aprovar", "rejeitar"})
    @DisplayName("AU-01: a dona da reserva, com perfil SOLICITANTE, não decide sobre ela e nada muda")
    void donaComPerfilSolicitanteNaoDecideSobreAPropriaReserva(String acao) {
        var reserva = solicitar(laboratorio);
        reserva.retirarMudancas();

        assertThatThrownBy(() -> decidir(reserva, acao, ana)).isInstanceOf(OperacaoNaoPermitidaException.class);

        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.SOLICITADA);
        assertThat(reserva.getAprovador()).isNull();
        assertThat(reserva.retirarMudancas()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"SOLICITANTE", "ADMINISTRADOR"})
    @DisplayName("AP-05: só o perfil RESPONSAVEL aprova; outro perfil é recusado e a reserva continua SOLICITADA")
    void somenteOPerfilResponsavelAprova(Perfil perfil) {
        var reserva = solicitar(laboratorio);
        var outro = perfil == Perfil.SOLICITANTE ? bruno : adm;

        assertThatThrownBy(() -> reserva.aprovar(outro, AGORA)).isInstanceOf(OperacaoNaoPermitidaException.class)
                .hasMessage("Somente um RESPONSAVEL pode aprovar reservas.");

        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.SOLICITADA);
    }

    @Test
    @DisplayName("AU-04: RESPONSAVEL não aprova reserva solicitada por ele mesmo; outro RESPONSAVEL aprova")
    void responsavelNaoAprovaReservaQueEleMesmoSolicitou() {
        var daRita = Reserva.solicitar(rita, List.of(laboratorio), PERIODO, "Reunião", AGORA);

        assertThatThrownBy(() -> daRita.aprovar(rita, AGORA)).isInstanceOf(OperacaoNaoPermitidaException.class)
                .hasMessage("Você não pode aprovar ou rejeitar uma reserva solicitada por você.");
        assertThat(daRita.getStatus()).isEqualTo(StatusReserva.SOLICITADA);

        daRita.aprovar(raul, AGORA);
        assertThat(daRita.getAprovador()).isSameAs(raul);
    }

    @Test
    @DisplayName("AP-06: RESPONSAVEL rejeita com motivo: REJEITADA, motivo registrado e horário liberado")
    void responsavelRejeitaComMotivoEOHorarioELiberado() {
        var reserva = solicitar(laboratorio);
        reserva.retirarMudancas();

        reserva.rejeitar(rita, "Laboratório reservado para prova", AGORA);

        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.REJEITADA);
        assertThat(reserva.retirarMudancas()).singleElement().satisfies(m -> {
            assertThat(m.novo()).isEqualTo(StatusReserva.REJEITADA);
            assertThat(m.motivo()).isEqualTo("Laboratório reservado para prova");
        });
        assertThat(reserva.getOcupacoes()).allSatisfy(o -> assertThat(o.isBloqueante()).isFalse());
    }

    static void decidir(Reserva reserva, String acao, Usuario autor) {
        if (acao.equals("aprovar")) {
            reserva.aprovar(autor, AGORA);
        } else {
            reserva.rejeitar(autor, "Sem vaga", AGORA);
        }
    }
}
