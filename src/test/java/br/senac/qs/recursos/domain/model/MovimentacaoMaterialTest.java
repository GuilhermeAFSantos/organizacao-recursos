package br.senac.qs.recursos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Retirada e devolução de materiais (RF09). Reserva de 10:00 às 12:00 (Brasília) = 13:00Z às 15:00Z. */
@DisplayName("Retirada e devolução de material")
class MovimentacaoMaterialTest {

    static final Instant INICIO = Instant.parse("2026-12-01T13:00:00Z");
    static final Instant FIM = Instant.parse("2026-12-01T15:00:00Z");
    static final Instant ANTES = Instant.parse("2026-12-01T10:00:00Z");

    final Usuario adm = new Usuario("adm@senac.br", Perfil.ADMINISTRADOR);
    final Usuario ana = new Usuario("ana@senac.br", Perfil.SOLICITANTE);
    final Usuario rita = new Usuario("rita@senac.br", Perfil.RESPONSAVEL);

    Material projetor;
    Reserva reserva;

    @BeforeEach
    void reservaAprovadaComProjetor() {
        projetor = Material.cadastrar(adm, "Projetor", false, "PAT-1", "Projetor", EstadoConservacao.BOM);
        reserva = Reserva.solicitar(ana, List.of(new Sala("Sala 101", false), projetor),
                new PeriodoReserva(INICIO, FIM), "Aula", ANTES);
    }

    MovimentacaoMaterial retirada() {
        return MovimentacaoMaterial.retirar(reserva, projetor, rita, null, null, INICIO);
    }

    @Nested
    @DisplayName("Retirada")
    class Retirada {

        @Test
        @DisplayName("registra quem entregou, quando e o estado do item na saída")
        void registra() {
            var retirada = MovimentacaoMaterial.retirar(reserva, projetor, rita, null, "Com cabo HDMI", INICIO);

            assertThat(retirada.getTipo()).isEqualTo(TipoMovimentacao.RETIRADA);
            assertThat(retirada.getMaterial()).isSameAs(projetor);
            assertThat(retirada.getReserva()).isSameAs(reserva);
            assertThat(retirada.getRegistradoPor()).isSameAs(rita);
            assertThat(retirada.getDataHora()).isEqualTo(INICIO);
            assertThat(retirada.getEstadoConservacao()).isEqualTo(EstadoConservacao.BOM);
            assertThat(retirada.getObservacao()).isEqualTo("Com cabo HDMI");
        }

        @Test
        @DisplayName("o ADMINISTRADOR também registra; o SOLICITANTE não (403)")
        void permissao() {
            assertThat(MovimentacaoMaterial.retirar(reserva, projetor, adm, null, null, INICIO)).isNotNull();
            assertThatThrownBy(() -> MovimentacaoMaterial.retirar(reserva, projetor, ana, null, null, INICIO))
                    .isInstanceOf(OperacaoNaoPermitidaException.class)
                    .hasMessage("Somente um RESPONSAVEL ou um ADMINISTRADOR registra retirada e devolução de materiais.");
        }

        @Test
        @DisplayName("material que não faz parte da reserva é recusado")
        void foraDaReserva() {
            var outro = Material.cadastrar(adm, "Notebook", false, "PAT-2", "Notebook", EstadoConservacao.BOM);

            assertThatThrownBy(() -> MovimentacaoMaterial.retirar(reserva, outro, rita, null, null, INICIO))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("O material Notebook (PAT-2) não faz parte desta reserva.");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = StatusReserva.class, names = {"SOLICITADA", "REJEITADA", "CANCELADA"})
        @DisplayName("só de reserva aprovada ou em uso")
        void estado(StatusReserva estado) {
            var laboratorio = Reserva.solicitar(ana, List.of(new Sala("Lab", true), projetor),
                    new PeriodoReserva(INICIO, FIM), "Aula", ANTES);
            if (estado == StatusReserva.REJEITADA) {
                laboratorio.rejeitar(rita, "Sem vaga", ANTES);
            } else if (estado == StatusReserva.CANCELADA) {
                laboratorio.cancelar(ana, null, ANTES);
            }

            assertThatThrownBy(() -> MovimentacaoMaterial.retirar(laboratorio, projetor, rita, null, null, INICIO))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Só é possível retirar material de reserva aprovada ou em uso.");
        }

        @Test
        @DisplayName("em uso também pode")
        void emUso() {
            reserva.iniciarUso(rita, INICIO);

            assertThat(retirada().getTipo()).isEqualTo(TipoMovimentacao.RETIRADA);
        }

        @Test
        @DisplayName("já retirado nesta reserva e não devolvido: recusado")
        void jaRetirado() {
            var primeira = retirada();

            assertThatThrownBy(() -> MovimentacaoMaterial.retirar(reserva, projetor, rita, primeira, null, INICIO))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("O material Projetor (PAT-1) já foi retirado e ainda não foi devolvido.");
        }

        @Test
        @DisplayName("ainda com quem usou na reserva anterior: recusado, dizendo de qual reserva")
        void comOutraPessoa() {
            var anterior = Reserva.solicitar(ana, List.of(new Sala("Sala 102", false), projetor),
                    new PeriodoReserva(ANTES, INICIO), "Aula", ANTES.minus(Duration.ofDays(1)));
            var retiradaAnterior = MovimentacaoMaterial.retirar(anterior, projetor, rita, null, null, ANTES);

            assertThatThrownBy(() -> MovimentacaoMaterial.retirar(reserva, projetor, rita, retiradaAnterior, null,
                    INICIO))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("O material Projetor (PAT-1) ainda não foi devolvido de outra reserva.");
        }

        @Test
        @DisplayName("material danificado depois da reserva não é entregue")
        void danificado() {
            projetor.alterar(adm, "Projetor", false, "PAT-1", "Projetor", EstadoConservacao.DANIFICADO);

            assertThatThrownBy(this::retiradaDoProjetor)
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("O material Projetor (PAT-1) está danificado e não pode ser retirado.");
        }

        void retiradaDoProjetor() {
            retirada();
        }
    }

    @Nested
    @DisplayName("Devolução")
    class Devolucao {

        @Test
        @DisplayName("registra o estado de volta e o material passa a ter esse estado")
        void registra() {
            var retirada = retirada();

            var devolucao = MovimentacaoMaterial.devolver(reserva, projetor, rita, retirada, EstadoConservacao.REGULAR,
                    "Riscos na lente", FIM);

            assertThat(devolucao.getTipo()).isEqualTo(TipoMovimentacao.DEVOLUCAO);
            assertThat(devolucao.getEstadoConservacao()).isEqualTo(EstadoConservacao.REGULAR);
            assertThat(projetor.getEstadoConservacao()).isEqualTo(EstadoConservacao.REGULAR);
            assertThat(devolucao.isAtrasada()).as("devolvido no fim: no prazo").isFalse();
        }

        @Test
        @DisplayName("fronteira do prazo: 1 minuto depois do fim é devolução atrasada")
        void atrasada() {
            var devolucao = MovimentacaoMaterial.devolver(reserva, projetor, rita, retirada(), EstadoConservacao.BOM,
                    null, FIM.plus(Duration.ofMinutes(1)));

            assertThat(devolucao.isAtrasada()).isTrue();
        }

        @ParameterizedTest(name = "observação \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        @DisplayName("danificado exige descrever o dano; nada muda se faltar")
        void danoExigeObservacao(String observacao) {
            var retirada = retirada();

            assertThatThrownBy(() -> MovimentacaoMaterial.devolver(reserva, projetor, rita, retirada,
                    EstadoConservacao.DANIFICADO, observacao, FIM))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Descreva o dano na observação.");
            assertThat(projetor.getEstadoConservacao()).isEqualTo(EstadoConservacao.BOM);
        }

        @Test
        @DisplayName("danificado com observação: o material fica DANIFICADO e deixa de ser reservável")
        void danificado() {
            MovimentacaoMaterial.devolver(reserva, projetor, rita, retirada(), EstadoConservacao.DANIFICADO,
                    "Lâmpada queimada", FIM);

            assertThat(projetor.getEstadoConservacao()).isEqualTo(EstadoConservacao.DANIFICADO);
            assertThatThrownBy(projetor::exigirReservavel).isInstanceOf(RegraNegocioException.class);
        }

        @Test
        @DisplayName("sem retirada pendente nesta reserva: recusado")
        void semRetirada() {
            var retirada = retirada();
            var devolucao = MovimentacaoMaterial.devolver(reserva, projetor, rita, retirada, EstadoConservacao.BOM,
                    null, FIM);

            assertThatThrownBy(() -> MovimentacaoMaterial.devolver(reserva, projetor, rita, null,
                    EstadoConservacao.BOM, null, FIM))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("O material Projetor (PAT-1) não está retirado nesta reserva.");
            assertThatThrownBy(() -> MovimentacaoMaterial.devolver(reserva, projetor, rita, devolucao,
                    EstadoConservacao.BOM, null, FIM))
                    .isInstanceOf(RegraNegocioException.class);
        }

        @Test
        @DisplayName("estado de conservação é obrigatório; SOLICITANTE não registra")
        void obrigatoriosEPermissao() {
            var retirada = retirada();

            assertThatThrownBy(() -> MovimentacaoMaterial.devolver(reserva, projetor, rita, retirada, null, null, FIM))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Informe o estado de conservação na devolução.");
            assertThatThrownBy(() -> MovimentacaoMaterial.devolver(reserva, projetor, ana, retirada,
                    EstadoConservacao.BOM, null, FIM))
                    .isInstanceOf(OperacaoNaoPermitidaException.class);
        }

        @Test
        @DisplayName("devolução vale mesmo depois de a reserva ser concluída")
        void depoisDeConcluida() {
            reserva.iniciarUso(rita, INICIO);
            var retirada = retirada();
            reserva.concluir(rita, FIM);

            assertThat(MovimentacaoMaterial.devolver(reserva, projetor, rita, retirada, EstadoConservacao.BOM, null,
                    FIM).getTipo()).isEqualTo(TipoMovimentacao.DEVOLUCAO);
        }
    }
}
