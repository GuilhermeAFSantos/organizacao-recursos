package br.senac.qs.recursos.application.usuario;

import java.time.Clock;
import java.util.List;

import br.senac.qs.recursos.application.UsuarioDoToken;
import br.senac.qs.recursos.domain.exception.CadastroDuplicadoException;
import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import br.senac.qs.recursos.domain.model.Perfil;
import br.senac.qs.recursos.domain.model.RegistroAuditoria;
import br.senac.qs.recursos.domain.model.Usuario;
import br.senac.qs.recursos.domain.repository.RegistroAuditoriaRepository;
import br.senac.qs.recursos.domain.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestão de usuários (RF01) com auditoria de cada mudança (RF10). A política de senha é validada na
 * entrada da API ({@code @SenhaForte}); aqui a senha só é transformada em hash BCrypt.
 */
@Service
public class GerenciarUsuarios {

    private final UsuarioDoToken usuarioDoToken;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder senhas;
    private final RegistroAuditoriaRepository auditoria;
    private final Clock clock;

    public GerenciarUsuarios(UsuarioDoToken usuarioDoToken, UsuarioRepository usuarios, PasswordEncoder senhas,
                             RegistroAuditoriaRepository auditoria, Clock clock) {
        this.usuarioDoToken = usuarioDoToken;
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public UsuarioResumo cadastrar(String emailAutor, String nome, String email, String senha, Perfil perfil) {
        var autor = administrador(emailAutor);
        var normalizado = Usuario.normalizarEmail(email);
        if (usuarios.existsByEmailIgnoreCase(normalizado)) {
            throw new CadastroDuplicadoException("Já existe um usuário com o e-mail " + normalizado + ".");
        }
        var agora = clock.instant();
        var usuario = usuarios.save(Usuario.cadastrar(nome, normalizado, senhas.encode(senha), perfil, agora));
        auditoria.save(RegistroAuditoria.doUsuario(usuario.getId(), "CRIACAO", autor, agora));
        return UsuarioResumo.de(usuario);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResumo> listar() {
        return usuarios.findAllByOrderByNomeAsc().stream().map(UsuarioResumo::de).toList();
    }

    @Transactional
    public UsuarioResumo desativar(String emailAutor, Long usuarioId) {
        var autor = administrador(emailAutor);
        var usuario = buscar(usuarioId);
        usuario.desativar(autor);
        auditoria.save(RegistroAuditoria.doUsuario(usuario.getId(), "DESATIVACAO", autor, clock.instant()));
        return UsuarioResumo.de(usuario);
    }

    @Transactional
    public void redefinirSenha(String emailAutor, Long usuarioId, String novaSenha) {
        var autor = administrador(emailAutor);
        var usuario = buscar(usuarioId);
        usuario.trocarSenha(senhas.encode(novaSenha));
        auditoria.save(RegistroAuditoria.doUsuario(usuario.getId(), "SENHA_REDEFINIDA", autor, clock.instant()));
    }

    @Transactional
    public void alterarPropriaSenha(String email, String senhaAtual, String novaSenha) {
        var usuario = usuarioDoToken.ativo(email);
        if (!senhas.matches(senhaAtual, usuario.getSenhaHash())) {
            throw new RegraNegocioException("A senha atual não confere.");
        }
        usuario.trocarSenha(senhas.encode(novaSenha));
        auditoria.save(RegistroAuditoria.doUsuario(usuario.getId(), "SENHA_ALTERADA", usuario, clock.instant()));
    }

    private Usuario administrador(String email) {
        var autor = usuarioDoToken.ativo(email);
        if (autor.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new OperacaoNaoPermitidaException("Somente um ADMINISTRADOR pode gerenciar usuários.");
        }
        return autor;
    }

    private Usuario buscar(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário " + usuarioId + " não encontrado."));
    }
}
