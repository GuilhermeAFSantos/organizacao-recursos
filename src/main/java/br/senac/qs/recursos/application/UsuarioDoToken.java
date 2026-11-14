package br.senac.qs.recursos.application;

import br.senac.qs.recursos.domain.model.Usuario;
import br.senac.qs.recursos.domain.repository.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Usuário dono do token. O JWT pode ainda ser válido depois que o usuário foi desativado, por isso
 * cada caso de uso confere no banco: usuário inexistente ou inativo recebe 403.
 */
@Component
public class UsuarioDoToken {

    private final UsuarioRepository usuarios;

    public UsuarioDoToken(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public Usuario ativo(String email) {
        return usuarios.findByEmailIgnoreCase(email)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new AccessDeniedException("usuário do token inexistente ou inativo"));
    }
}
