package br.senac.qs.recursos.domain.model;

import static br.senac.qs.recursos.domain.model.StatusReserva.APROVADA;
import static br.senac.qs.recursos.domain.model.StatusReserva.SOLICITADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Uma reserva pode juntar sala, professor e materiais (RF04, RF05). É tudo ou nada: uma ocupação
 * por recurso, no mesmo período, e a aprovação automática só vale se NENHUM recurso for restrito.
 */
@DisplayName("Reserva com vários recursos")
class ReservaComVariosRecursosTest {

    static final Instant INICIO = Instant.parse("2026-12-01T13:00:00Z");
    static final Instant FIM = Instant.parse("2026-12-01T15:00:00Z");
    static final Instant ANTES = Instant.parse("2026-12-01T10:00:00Z");
    static final PeriodoReserva PERIODO = new PeriodoReserva(INICIO, FIM);

    final Usuario adm = new Usuario("adm@senac.br", Perfil.ADMINISTRADOR);
    final Usuario ana = new Usuario("ana@senac.br", Perfil.SOLICITANTE);
    final Sala sala = new Sala("Sala 101", false);

    Professor professor(boolean restrito) {
        return Professor.cadastrar(adm, "Carlos Lima", restrito, "P-1", null, Set.of());
    }

    Material projetor(EstadoConservacao estado) {
        return Material.cadastrar(adm, "Projetor", false, "PAT-1", "Projetor", estado);
    }

    @Test
    @DisplayName("uma ocupação por recurso, todas no mesmo período")
    void ocupacoes() {
        var professor = professor(false);
        var projetor = projetor(EstadoConservacao.BOM);

        var reserva = Reserva.solicitar(ana, List.of(sala, professor, projetor), PERIODO, "Aula", ANTES);

        assertThat(reserva.getOcupacoes()).extracting(Ocupacao::getRecurso).containsExactly(sala, professor, projetor);
        assertThat(reserva.getOcupacoes()).extracting(Ocupacao::getPeriodo).containsOnly(PERIODO);
    }

    @Test
    @DisplayName("nenhum recurso restrito: aprovação automática")
    void semRestrito() {
        var reserva = Reserva.solicitar(ana, List.of(sala, professor(false)), PERIODO, "Aula", ANTES);

        assertThat(reserva.getStatus()).isEqualTo(APROVADA);
    }

    @Test
    @DisplayName("basta um recurso restrito (o professor) para exigir aprovação")
    void umRestrito() {
        var reserva = Reserva.solicitar(ana, List.of(sala, professor(true)), PERIODO, "Aula", ANTES);

        assertThat(reserva.getStatus()).isEqualTo(SOLICITADA);
    }

    @Test
    @DisplayName("recurso desativado não é reservado, com mensagem no gênero certo")
    void desativado() {
        var professor = professor(false);
        professor.desativar(adm);
        var salaInativa = new Sala("Sala 102", false);
        salaInativa.desativar(adm);

        assertThatThrownBy(() -> Reserva.solicitar(ana, List.of(sala, professor), PERIODO, "Aula", ANTES))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O professor Carlos Lima está desativado e não aceita reservas.");
        assertThatThrownBy(() -> Reserva.solicitar(ana, List.of(salaInativa), PERIODO, "Aula", ANTES))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A sala Sala 102 está desativada e não aceita reservas.");
    }

    @Test
    @DisplayName("material danificado não é reservado; o patrimônio identifica o item")
    void danificado() {
        var projetor = projetor(EstadoConservacao.DANIFICADO);

        assertThatThrownBy(() -> Reserva.solicitar(ana, List.of(sala, projetor), PERIODO, "Aula", ANTES))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O material Projetor (PAT-1) está danificado e não pode ser reservado.");
    }

    @Test
    @DisplayName("material em estado REGULAR pode ser reservado")
    void regular() {
        var reserva = Reserva.solicitar(ana, List.of(sala, projetor(EstadoConservacao.REGULAR)), PERIODO, "Aula",
                ANTES);

        assertThat(reserva.getOcupacoes()).hasSize(2);
    }

    @Test
    @DisplayName("sem nenhum recurso não há reserva")
    void semRecurso() {
        assertThatThrownBy(() -> Reserva.solicitar(ana, List.of(), PERIODO, "Aula", ANTES))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Informe ao menos um recurso para reservar.");
    }
}
