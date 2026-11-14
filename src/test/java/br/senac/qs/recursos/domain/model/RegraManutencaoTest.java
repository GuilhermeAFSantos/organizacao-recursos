package br.senac.qs.recursos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest(name = "encerrada às {0} → termina às {1}")
    @CsvSource({
        "10:37:42, 10:38",
        "10:37:00, 10:37",
        "11:59:01, 12:00"
    })
    @DisplayName("MN-11: encerrar durante a manutenção a encurta até o próximo minuto cheio, e o recurso fica livre depois disso")
    void encerrarDuranteAManutencaoEncurtaAteOProximoMinutoCheio(String agora, String novoFim) {
        var manutencao = manutencaoDaSala101();
        var relogio = Clock.fixed(instante(agora), ZoneOffset.UTC);

        manutencao.encerrar(adm, relogio.instant());

        assertThat(manutencao.getPeriodo()).isEqualTo(periodo("10:00", novoFim));
        assertThat(manutencao.getOcupacoes()).allSatisfy(o -> {
            assertThat(o.isBloqueante()).isTrue();
            assertThat(o.getPeriodo()).isEqualTo(periodo("10:00", novoFim));
        });
    }

    @ParameterizedTest(name = "encerrada às {0}")
    @ValueSource(strings = {"07:00", "10:00"})
    @DisplayName("MN-11: encerrar antes do início (ou exatamente no início) desfaz a manutenção e libera o horário inteiro")
    void encerrarAntesDoInicioDesfazAManutencao(String agora) {
        var manutencao = manutencaoDaSala101();
        var relogio = Clock.fixed(instante(agora), ZoneOffset.UTC);

        assertThatCode(() -> manutencao.encerrar(adm, relogio.instant())).doesNotThrowAnyException();

        assertThat(manutencao.isAtivo()).isFalse();
        assertThat(manutencao.getOcupacoes()).allSatisfy(o -> assertThat(o.isBloqueante()).isFalse());
    }

    @ParameterizedTest(name = "encerrada às {0}")
    @ValueSource(strings = {"12:00", "13:00"})
    @DisplayName("MN-11: encerrar no fim ou depois é recusado, porque a manutenção já terminou")
    void encerrarNoFimOuDepoisERecusado(String agora) {
        var manutencao = manutencaoDaSala101();
        var relogio = Clock.fixed(instante(agora), ZoneOffset.UTC);

        assertThatThrownBy(() -> manutencao.encerrar(adm, relogio.instant())).isInstanceOf(RegraNegocioException.class)
                .hasMessage("Este bloqueio já terminou.");

        assertThat(manutencao.getPeriodo()).isEqualTo(MANUTENCAO);
    }

    @Test
    @DisplayName("MN-11: encerrar uma manutenção já desfeita (encerrada antes de começar) é recusado")
    void encerrarManutencaoJaDesfeitaERecusado() {
        var manutencao = manutencaoDaSala101();
        var relogio = Clock.fixed(ANTES, ZoneOffset.UTC);
        manutencao.encerrar(adm, relogio.instant());

        assertThatThrownBy(() -> manutencao.encerrar(adm, relogio.instant())).isInstanceOf(RegraNegocioException.class)
                .hasMessage("Este bloqueio já foi encerrado.");
    }

    Bloqueio manutencaoDaSala101() {
        return Bloqueio.registrar(adm, sala101, TipoBloqueio.MANUTENCAO, MANUTENCAO, "Troca do projetor", ANTES);
    }

    static PeriodoReserva periodo(String inicio, String fim) {
        return new PeriodoReserva(instante(inicio), instante(fim));
    }

    static Instant instante(String hora) {
        return LocalTime.parse(hora).atDate(DIA).atZone(BRASILIA).toInstant();
    }
}
