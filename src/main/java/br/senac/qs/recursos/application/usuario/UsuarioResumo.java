package br.senac.qs.recursos.application.usuario;

import br.senac.qs.recursos.domain.model.Perfil;
import br.senac.qs.recursos.domain.model.Usuario;

/** Dados públicos do usuário. Nunca inclui o hash da senha. */
public record UsuarioResumo(Long id, String nome, String email, Perfil perfil, boolean ativo) {

    static UsuarioResumo de(Usuario usuario) {
        return new UsuarioResumo(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil(),
                usuario.isAtivo());
    }
}
