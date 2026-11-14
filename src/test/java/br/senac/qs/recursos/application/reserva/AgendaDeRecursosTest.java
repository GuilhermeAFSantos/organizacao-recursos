package br.senac.qs.recursos.application.reserva;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import br.senac.qs.recursos.domain.exception.ConflitoHorarioException;
import br.senac.qs.recursos.domain.model.Bloqueio;
import br.senac.qs.recursos.domain.model.PeriodoReserva;
import br.senac.qs.recursos.domain.model.Perfil;
import br.senac.qs.recursos.domain.model.Sala;
import br.senac.qs.recursos.domain.model.TipoBloqueio;
import br.senac.qs.recursos.domain.model.TipoSala;
import br.senac.qs.recursos.domain.model.Usuario;
import br.senac.qs.recursos.domain.repository.OcupacaoRepository;
import br.senac.qs.recursos.domain.repository.RecursoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regra 3, mensagens (MN-02, MN-10), refeitas em TDD estrito. Teste de unidade isolado: o
 * repositório de ocupações é simulado e devolve a ocupação da manutenção que conflita.
 */
@DisplayName("Regra 3: mensagem ao recusar reserva por manutenção ou bloqueio")
class AgendaDeRecursosTest {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final LocalDate DIA = LocalDate.of(2026, 12, 1);

    final OcupacaoRepository ocupacoes = mock(OcupacaoRepository.class);
    final AgendaDeRecursos agenda = new AgendaDeRecursos(mock(RecursoRepository.class), ocupacoes);

    final Usuario adm = Usuario.cadastrar("Adm", "adm@senac.br", "hash", Perfil.ADMINISTRADOR, instante("07:00"));
    final Usuario ana = Usuario.cadastrar("Ana", "ana@senac.br", "hash", Perfil.SOLICITANTE, instante("07:00"));
    final Sala sala101 = Sala.cadastrar(adm, "Sala 101", false, 40, "A", 1, TipoSala.AULA);

    @Test
    @DisplayName("MN-02: reserva recusada por manutenção explica a sala, o horário e a data, sem detalhe interno")
    void recusaPorManutencaoExplicaSalaHorarioEData() {
        var manutencao = Bloqueio.registrar(adm, sala101, TipoBloqueio.MANUTENCAO, periodo("10:00", "12:00"),
                "Troca do projetor", instante("07:00"));
        when(ocupacoes.sobrepostas(any(), any(), any())).thenReturn(manutencao.getOcupacoes());

        assertThatThrownBy(() -> agenda.garantirLivre(List.of(sala101), periodo("10:30", "11:30"), null, ana))
                .isInstanceOf(ConflitoHorarioException.class)
                .hasMessage("A sala Sala 101 está em manutenção das 10:00 às 12:00 de 01/12/2026. "
                        + "Escolha outro horário ou outra sala.");
    }

    static PeriodoReserva periodo(String inicio, String fim) {
        return new PeriodoReserva(instante(inicio), instante(fim));
    }

    static Instant instante(String hora) {
        return LocalTime.parse(hora).atDate(DIA).atZone(BRASILIA).toInstant();
    }
}
