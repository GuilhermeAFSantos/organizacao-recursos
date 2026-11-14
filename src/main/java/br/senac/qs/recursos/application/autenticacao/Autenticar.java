package br.senac.qs.recursos.application.autenticacao;

import java.util.UUID;
import java.time.Clock;

import br.senac.qs.recursos.domain.exception.CredenciaisInvalidasException;
import br.senac.qs.recursos.domain.exception.MuitasTentativasException;
import br.senac.qs.recursos.domain.model.Perfil;
import br.senac.qs.recursos.domain.model.Usuario;
import br.senac.qs.recursos.domain.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login (RF01). Decisões de segurança:
 * <ul>
 *   <li>Mesma resposta para e-mail inexistente, senha errada e usuário inativo.</li>
 *   <li>Tempo de resposta equivalente: sem usuário, a senha é comparada mesmo assim com um hash
 *       fictício, para o BCrypt (lento de propósito) não denunciar quais e-mails existem.</li>
 *   <li>Força bruta: a falha é gravada mesmo com o login recusado ({@code noRollbackFor}).</li>
 * </ul>
 */
@Service
public class Autenticar {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder senhas;
    private final EmissorDeToken emissor;
    private final Clock clock;
    private final String hashFicticio;

    public Autenticar(UsuarioRepository usuarios, PasswordEncoder senhas, EmissorDeToken emissor, Clock clock) {
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.emissor = emissor;
        this.clock = clock;
        // Valor aleatório a cada inicialização: nenhum texto fixo que pareça senha no código (SonarCloud
        // S6437) e nada que alguém possa adivinhar. Só serve para gastar o mesmo tempo do BCrypt.
        this.hashFicticio = senhas.encode(UUID.randomUUID().toString());
    }

    public record Sessao(String token, java.time.Instant expiraEm, Perfil perfil) {
    }

    @Transactional(noRollbackFor = {CredenciaisInvalidasException.class, MuitasTentativasException.class})
    public Sessao executar(String email, String senha) {
        var agora = clock.instant();
        var encontrado = usuarios.findByEmailIgnoreCase(Usuario.normalizarEmail(email));
        if (encontrado.isEmpty() || !encontrado.get().isAtivo()) {
            senhas.matches(senha, hashFicticio);
            throw new CredenciaisInvalidasException();
        }
        var usuario = encontrado.get();
        if (usuario.estaBloqueado(agora)) {
            throw new MuitasTentativasException();
        }
        if (!senhas.matches(senha, usuario.getSenhaHash())) {
            usuario.registrarFalhaDeLogin(agora);
            throw new CredenciaisInvalidasException();
        }
        usuario.registrarLoginComSucesso();
        var token = emissor.emitir(usuario);
        return new Sessao(token.token(), token.expiraEm(), usuario.getPerfil());
    }
}
