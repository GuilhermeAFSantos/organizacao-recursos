package br.senac.qs.recursos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Cadastro de salas, professores e materiais (RF02): invariantes do domínio. */
@DisplayName("Cadastro de recursos")
class CadastroRecursosTest {

    final Usuario adm = new Usuario("adm@senac.br", Perfil.ADMINISTRADOR);

    Sala sala101() {
        return Sala.cadastrar(adm, "  Sala 101  ", false, 40, " A ", 1, TipoSala.AULA);
    }

    @Nested
    @DisplayName("Sala")
    class Salas {

        @Test
        @DisplayName("é cadastrada ativa, com textos sem espaços nas pontas")
        void cadastra() {
            var sala = sala101();

            assertThat(sala.getTipo()).isEqualTo(TipoRecurso.SALA);
            assertThat(sala.getNome()).isEqualTo("Sala 101");
            assertThat(sala.getBloco()).isEqualTo("A");
            assertThat(sala.getCapacidade()).isEqualTo(40);
            assertThat(sala.getAndar()).isEqualTo(1);
            assertThat(sala.getTipoSala()).isEqualTo(TipoSala.AULA);
            assertThat(sala.isAtivo()).isTrue();
            assertThat(sala.isRestrito()).isFalse();
        }

        @ParameterizedTest(name = "capacidade {0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("capacidade precisa ser positiva (fronteira: 1 é aceita)")
        void capacidadePositiva(int capacidade) {
            assertThatThrownBy(() -> Sala.cadastrar(adm, "Sala", false, capacidade, "A", 1, TipoSala.AULA))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("A capacidade da sala deve ser maior que zero.");
            assertThat(Sala.cadastrar(adm, "Sala", false, 1, "A", 1, TipoSala.AULA).getCapacidade()).isEqualTo(1);
        }

        @Test
        @DisplayName("andar pode ser negativo (subsolo)")
        void subsolo() {
            assertThat(Sala.cadastrar(adm, "Depósito", false, 5, "A", -1, TipoSala.REUNIAO).getAndar()).isEqualTo(-1);
        }

        @ParameterizedTest(name = "nome \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        @DisplayName("nome é obrigatório")
        void nomeObrigatorio(String nome) {
            assertThatThrownBy(() -> Sala.cadastrar(adm, nome, false, 10, "A", 1, TipoSala.AULA))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Informe o nome do recurso.");
        }

        @Test
        @DisplayName("bloco é obrigatório")
        void blocoObrigatorio() {
            assertThatThrownBy(() -> Sala.cadastrar(adm, "Sala", false, 10, " ", 1, TipoSala.AULA))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Informe o bloco da sala.");
        }

        @Test
        @DisplayName("tipo de sala é obrigatório")
        void tipoObrigatorio() {
            assertThatThrownBy(() -> Sala.cadastrar(adm, "Sala", false, 10, "A", 1, null))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Informe o tipo da sala.");
        }

        @Test
        @DisplayName("alteração troca todos os dados, inclusive se é restrita")
        void altera() {
            var sala = sala101();

            sala.alterar(adm, "Laboratório 3", true, 25, "B", 2, TipoSala.LABORATORIO);

            assertThat(sala.getNome()).isEqualTo("Laboratório 3");
            assertThat(sala.isRestrito()).isTrue();
            assertThat(sala.getCapacidade()).isEqualTo(25);
            assertThat(sala.getBloco()).isEqualTo("B");
            assertThat(sala.getAndar()).isEqualTo(2);
            assertThat(sala.getTipoSala()).isEqualTo(TipoSala.LABORATORIO);
        }
    }

    @Nested
    @DisplayName("Professor")
    class Professores {

        @Test
        @DisplayName("guarda matrícula, e-mail normalizado e competências em ordem alfabética")
        void cadastra() {
            var competencias = new LinkedHashSet<>(List.of(Competencia.nova("Java"), Competencia.nova("Banco de Dados")));

            var professor = Professor.cadastrar(adm, "Carlos Lima", " P-123 ", " Carlos@Senac.BR ", competencias);

            assertThat(professor.getTipo()).isEqualTo(TipoRecurso.PROFESSOR);
            assertThat(professor.getMatricula()).isEqualTo("P-123");
            assertThat(professor.getEmail()).isEqualTo("carlos@senac.br");
            assertThat(professor.nomesDasCompetencias()).containsExactly("Banco de Dados", "Java");
            assertThat(professor.isRestrito()).isTrue();
        }

        @ParameterizedTest(name = "e-mail \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        @DisplayName("e-mail é opcional")
        void emailOpcional(String email) {
            assertThat(Professor.cadastrar(adm, "Carlos", "P-1", email, Set.of()).getEmail()).isNull();
        }

        @Test
        @DisplayName("matrícula é obrigatória")
        void matriculaObrigatoria() {
            assertThatThrownBy(() -> Professor.cadastrar(adm, "Carlos", " ", null, Set.of()))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Informe a matrícula do professor.");
        }

        @Test
        @DisplayName("alteração substitui as competências")
        void altera() {
            var professor = Professor.cadastrar(adm, "Carlos", "P-1", null, Set.of(Competencia.nova("Java")));

            professor.alterar(adm, "Carlos Lima", "P-2", "c@senac.br", Set.of(Competencia.nova("Redes")));

            assertThat(professor.getNome()).isEqualTo("Carlos Lima");
            assertThat(professor.getMatricula()).isEqualTo("P-2");
            assertThat(professor.nomesDasCompetencias()).containsExactly("Redes");
        }
    }

    @Test
    @DisplayName("Q13/D4: todo professor é restrito, sem opção no cadastro")
    void professorSempreRestrito() {
        var professor = Professor.cadastrar(adm, "Carlos", "P-9", null, Set.of());
        assertThat(professor.isRestrito()).as("no cadastro").isTrue();

        professor.alterar(adm, "Carlos Lima", "P-9", null, Set.of());
        assertThat(professor.isRestrito()).as("na alteração").isTrue();
    }

    @Nested
    @DisplayName("Competência")
    class Competencias {

        @Test
        @DisplayName("nome sem espaços extras; a chave ignora maiúsculas")
        void normaliza() {
            assertThat(Competencia.nova("  Banco \t de  Dados ").getNome()).isEqualTo("Banco de Dados");
            assertThat(Competencia.chave(" JAVA ")).isEqualTo(Competencia.chave("java"));
        }

        @Test
        @DisplayName("nome é obrigatório")
        void obrigatorio() {
            assertThatThrownBy(() -> Competencia.nova(" "))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Informe o nome da competência.");
        }
    }

    @Nested
    @DisplayName("Material")
    class Materiais {

        @Test
        @DisplayName("guarda patrimônio, tipo e estado de conservação")
        void cadastra() {
            var material = Material.cadastrar(adm, "Projetor Epson", false, " PAT-0012 ", " Projetor ",
                    EstadoConservacao.BOM);

            assertThat(material.getTipo()).isEqualTo(TipoRecurso.MATERIAL);
            assertThat(material.getPatrimonio()).isEqualTo("PAT-0012");
            assertThat(material.getTipoMaterial()).isEqualTo("Projetor");
            assertThat(material.getEstadoConservacao()).isEqualTo(EstadoConservacao.BOM);
        }

        @Test
        @DisplayName("patrimônio e estado de conservação são obrigatórios")
        void obrigatorios() {
            assertThatThrownBy(() -> Material.cadastrar(adm, "Projetor", false, "", "Projetor", EstadoConservacao.BOM))
                    .isInstanceOf(RegraNegocioException.class).hasMessage("Informe o patrimônio do material.");
            assertThatThrownBy(() -> Material.cadastrar(adm, "Projetor", false, "PAT-1", "Projetor", null))
                    .isInstanceOf(RegraNegocioException.class).hasMessage("Informe o estado de conservação.");
        }

        @Test
        @DisplayName("alteração registra, por exemplo, que o item foi danificado")
        void altera() {
            var material = Material.cadastrar(adm, "Projetor", false, "PAT-1", "Projetor", EstadoConservacao.BOM);

            material.alterar(adm, "Projetor", false, "PAT-1", "Projetor", EstadoConservacao.DANIFICADO);

            assertThat(material.getEstadoConservacao()).isEqualTo(EstadoConservacao.DANIFICADO);
        }
    }

    @Nested
    @DisplayName("Permissão e ciclo de vida")
    class CicloDeVida {

        @ParameterizedTest(name = "{0} não cadastra")
        @EnumSource(value = Perfil.class, names = {"SOLICITANTE", "RESPONSAVEL"})
        @DisplayName("só o ADMINISTRADOR cadastra, altera, desativa e reativa")
        void somenteAdministrador(Perfil perfil) {
            var outro = new Usuario("x@senac.br", perfil);
            var sala = sala101();
            var mensagem = "Somente um ADMINISTRADOR pode cadastrar ou alterar recursos.";

            assertThatThrownBy(() -> Sala.cadastrar(outro, "Sala", false, 10, "A", 1, TipoSala.AULA))
                    .isInstanceOf(OperacaoNaoPermitidaException.class).hasMessage(mensagem);
            assertThatThrownBy(() -> Professor.cadastrar(outro, "Carlos", "P-1", null, Set.of()))
                    .isInstanceOf(OperacaoNaoPermitidaException.class).hasMessage(mensagem);
            assertThatThrownBy(() -> Material.cadastrar(outro, "Projetor", false, "PAT-1", "Projetor",
                    EstadoConservacao.BOM)).isInstanceOf(OperacaoNaoPermitidaException.class).hasMessage(mensagem);
            assertThatThrownBy(() -> sala.alterar(outro, "Sala", false, 10, "A", 1, TipoSala.AULA))
                    .isInstanceOf(OperacaoNaoPermitidaException.class).hasMessage(mensagem);
            assertThatThrownBy(() -> sala.desativar(outro))
                    .isInstanceOf(OperacaoNaoPermitidaException.class).hasMessage(mensagem);
            assertThat(sala.isAtivo()).isTrue();
        }

        @Test
        @DisplayName("desativa e reativa; repetir a operação é recusado")
        void desativaEReativa() {
            var sala = sala101();

            sala.desativar(adm);
            assertThat(sala.isAtivo()).isFalse();
            assertThatThrownBy(() -> sala.desativar(adm))
                    .isInstanceOf(RegraNegocioException.class).hasMessage("O recurso já está inativo.");

            sala.reativar(adm);
            assertThat(sala.isAtivo()).isTrue();
            assertThatThrownBy(() -> sala.reativar(adm))
                    .isInstanceOf(RegraNegocioException.class).hasMessage("O recurso já está ativo.");
        }
    }
}
