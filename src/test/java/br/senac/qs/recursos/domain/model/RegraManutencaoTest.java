package br.senac.qs.recursos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Regra 3 de docs/testes/bdd-aprovacao-bloqueio.md (recurso em manutenção), refeita em TDD estrito.
 * Manutenção da Sala 101 das 10:00 às 12:00 de 01/12/2026 (Brasília).
 */
@DisplayName("Regra 3: recurso em manutenção não pode ser reservado")
class RegraManutencaoTest {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final LocalDate DIA = LocalDate.of(2026, 12, 1);
    static final PeriodoReserva MANUTENCAO = periodo("10:00", "12:00");
    static final Instant ANTES = instante("07:00");

    final Sala sala101 = new Sala("Sala 101", false);
    final Usuario adm = new Usuario("adm@senac.br", Perfil.ADMINISTRADOR);

    @ParameterizedTest(name = "{0}: reserva [{1}, {2}) → conflita = {3}")
    @CsvSource({
        "F1,  08:00, 09:00, false",
        "F3,  08:00, 10:00, false",
        "F4,  08:00, 10:01, true",
        "F5,  10:30, 11:30, true",
        "F6,  10:00, 12:00, true",
        "F7,  09:00, 13:00, true",
        "F8,  11:59, 13:00, true",
        "F9,  12:00, 14:00, false",
        "F11, 13:00, 14:00, false"
    })
    @DisplayName("MN-01: fronteiras da manutenção; só encostar (F3, F9) não conflita e a regra vale nos dois sentidos")
    void fronteirasDoPeriodoDeManutencao(String caso, String inicio, String fim, boolean conflita) {
        var reserva = periodo(inicio, fim);

        assertThat(reserva.sobrepoe(MANUTENCAO)).isEqualTo(conflita);
        assertThat(MANUTENCAO.sobrepoe(reserva)).isEqualTo(conflita);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Perfil.class, names = {"SOLICITANTE", "RESPONSAVEL"})
    @DisplayName("MN-08: só o ADMINISTRADOR registra manutenção")
    void somenteOAdministradorRegistraManutencao(Perfil perfil) {
        var outro = new Usuario("outro@senac.br", perfil);

        assertThatThrownBy(() -> Bloqueio.registrar(outro, sala101, TipoBloqueio.MANUTENCAO, MANUTENCAO,
                "Troca do projetor", ANTES)).isInstanceOf(OperacaoNaoPermitidaException.class)
                .hasMessage("Somente um ADMINISTRADOR pode registrar ou encerrar bloqueios.");
    }

    static PeriodoReserva periodo(String inicio, String fim) {
        return new PeriodoReserva(instante(inicio), instante(fim));
    }

    static Instant instante(String hora) {
        return LocalTime.parse(hora).atDate(DIA).atZone(BRASILIA).toInstant();
    }
}
