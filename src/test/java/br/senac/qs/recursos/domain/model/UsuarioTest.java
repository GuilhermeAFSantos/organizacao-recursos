package br.senac.qs.recursos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Usuario: cadastro, bloqueio por tentativas e desativação (RF01)")
class UsuarioTest {

    static final Instant AGORA = Instant.parse("2026-12-01T12:00:00Z");

    Usuario ana() {
        return Usuario.cadastrar("Ana", "ana@senac.br", "hash", Perfil.SOLICITANTE, AGORA);
    }

    @Test
    @DisplayName("cadastro normaliza o e-mail (minúsculas, sem espaços) e cria usuário ativo")
    void cadastroNormalizaEmail() {
        var usuario = Usuario.cadastrar("Ana", "  Ana@Senac.BR ", "hash", Perfil.SOLICITANTE, AGORA);

        assertThat(usuario.getEmail()).isEqualTo("ana@senac.br");
        assertThat(usuario.isAtivo()).isTrue();
        assertThat(usuario.estaBloqueado(AGORA)).isFalse();
    }

    @Nested
    @DisplayName("Proteção contra força bruta: 5 falhas seguidas bloqueiam por 15 minutos")
    class ForcaBruta {

        @ParameterizedTest(name = "{0} falha(s) → bloqueado={1}")
        @CsvSource({"1, false", "4, false", "5, true"})
        @DisplayName("fronteira do número de tentativas")
        void numeroDeTentativas(int falhas, boolean bloqueado) {
            var usuario = ana();

            for (int i = 0; i < falhas; i++) {
                usuario.registrarFalhaDeLogin(AGORA);
            }

            assertThat(usuario.estaBloqueado(AGORA)).isEqualTo(bloqueado);
        }

        @ParameterizedTest(name = "{0} s depois da 5ª falha → bloqueado={1}")
        @CsvSource({"0, true", "899, true", "900, false", "3600, false"})
        @DisplayName("fronteira da duração: bloqueado até 15 min (900 s) depois, exclusive")
        void duracaoDoBloqueio(long segundosDepois, boolean bloqueado) {
            var usuario = ana();
            for (int i = 0; i < Usuario.MAXIMO_TENTATIVAS; i++) {
                usuario.registrarFalhaDeLogin(AGORA);
            }

            assertThat(usuario.estaBloqueado(AGORA.plusSeconds(segundosDepois))).isEqualTo(bloqueado);
        }

        @Test
        @DisplayName("login com sucesso zera a contagem: 4 falhas + sucesso + 4 falhas não bloqueia")
        void sucessoZeraContagem() {
            var usuario = ana();
            for (int i = 0; i < 4; i++) {
                usuario.registrarFalhaDeLogin(AGORA);
            }
            usuario.registrarLoginComSucesso();
            for (int i = 0; i < 4; i++) {
                usuario.registrarFalhaDeLogin(AGORA);
            }

            assertThat(usuario.estaBloqueado(AGORA)).isFalse();
        }

        @Test
        @DisplayName("passado o bloqueio, a contagem recomeça do zero")
        void contagemRecomeca() {
            var usuario = ana();
            for (int i = 0; i < Usuario.MAXIMO_TENTATIVAS; i++) {
                usuario.registrarFalhaDeLogin(AGORA);
            }
            Instant depois = AGORA.plus(Usuario.DURACAO_BLOQUEIO);

            usuario.registrarFalhaDeLogin(depois);

            assertThat(usuario.estaBloqueado(depois)).as("1 falha nova não bloqueia de novo").isFalse();
        }
    }

    @Nested
    @DisplayName("Desativação")
    class Desativacao {

        final Usuario adm = Usuario.cadastrar("Adm", "adm@senac.br", "hash", Perfil.ADMINISTRADOR, AGORA);

        @Test
        @DisplayName("ADMINISTRADOR desativa outro usuário")
        void administradorDesativa() {
            var usuario = ana();

            usuario.desativar(adm);

            assertThat(usuario.isAtivo()).isFalse();
        }

        @Test
        @DisplayName("quem não é ADMINISTRADOR não desativa")
        void naoAdministrador() {
            var usuario = ana();
            var rita = Usuario.cadastrar("Rita", "rita@senac.br", "hash", Perfil.RESPONSAVEL, AGORA);

            assertThatThrownBy(() -> usuario.desativar(rita))
                    .isInstanceOf(OperacaoNaoPermitidaException.class)
                    .hasMessage("Somente um ADMINISTRADOR pode gerenciar usuários.");
        }

        @Test
        @DisplayName("ADMINISTRADOR não desativa a si mesmo (evita ficar sem administrador)")
        void naoDesativaASiMesmo() {
            assertThatThrownBy(() -> adm.desativar(adm))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage("Você não pode desativar o próprio usuário.");
            assertThat(adm.isAtivo()).isTrue();
        }
    }

    @Test
    @DisplayName("trocar a senha substitui o hash")
    void trocaSenha() {
        var usuario = ana();

        usuario.trocarSenha("novo-hash");

        assertThat(usuario.getSenhaHash()).isEqualTo("novo-hash");
    }
}
