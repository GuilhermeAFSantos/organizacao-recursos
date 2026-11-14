package br.senac.qs.recursos.application.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import br.senac.qs.recursos.domain.model.Perfil;
import br.senac.qs.recursos.domain.model.Usuario;
import br.senac.qs.recursos.domain.repository.RegistroAuditoriaRepository;
import br.senac.qs.recursos.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Teste de unidade isolado (mocks): a criação do primeiro ADMINISTRADOR na inicialização. */
@DisplayName("GarantirPrimeiroAdministrador")
class GarantirPrimeiroAdministradorTest {

    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    PasswordEncoder senhas = mock(PasswordEncoder.class);
    RegistroAuditoriaRepository auditoria = mock(RegistroAuditoriaRepository.class);
    GarantirPrimeiroAdministrador casoDeUso = new GarantirPrimeiroAdministrador(usuarios, senhas, auditoria,
            Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC));

    @Test
    @DisplayName("já existe ADMINISTRADOR ativo: não cria nada")
    void jaExiste() {
        when(usuarios.existsByPerfilAndAtivoTrue(Perfil.ADMINISTRADOR)).thenReturn(true);

        casoDeUso.executar("admin@senac.br", "Admin2026");

        verify(usuarios, never()).save(any());
    }

    @ParameterizedTest(name = "email=[{0}] senha=[{1}] → não cria (só avisa no log)")
    @CsvSource({"'', ''", "admin@senac.br, ''", "'', Admin2026"})
    @DisplayName("sem ADMIN_EMAIL ou ADMIN_SENHA configurados: não cria")
    void semConfiguracao(String email, String senha) {
        casoDeUso.executar(email, senha);

        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("nenhum ADMINISTRADOR e configuração válida: cria com e-mail normalizado e senha em hash; auditado")
    void cria() {
        when(senhas.encode("Admin2026")).thenReturn("hash-bcrypt");
        when(usuarios.save(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        casoDeUso.executar(" Admin@Senac.br ", "Admin2026");

        var salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(salvo.capture());
        assertThat(salvo.getValue().getEmail()).isEqualTo("admin@senac.br");
        assertThat(salvo.getValue().getPerfil()).isEqualTo(Perfil.ADMINISTRADOR);
        assertThat(salvo.getValue().getSenhaHash()).isEqualTo("hash-bcrypt");
        verify(auditoria).save(any());
    }

    @Test
    @DisplayName("ADMIN_SENHA fraca: a aplicação não sobe (falha rápida com mensagem clara)")
    void senhaFraca() {
        assertThatIllegalStateException()
                .isThrownBy(() -> casoDeUso.executar("admin@senac.br", "admin"))
                .withMessageContaining("ADMIN_SENHA");
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("ADMIN_EMAIL já usado por outro usuário: a aplicação não sobe")
    void emailEmUso() {
        when(usuarios.existsByEmailIgnoreCase("admin@senac.br")).thenReturn(true);

        assertThatIllegalStateException()
                .isThrownBy(() -> casoDeUso.executar("admin@senac.br", "Admin2026"))
                .withMessageContaining("ADMIN_EMAIL");
    }
}
