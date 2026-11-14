package br.senac.qs.recursos.application.autenticacao;

import java.time.Instant;

import br.senac.qs.recursos.domain.model.Usuario;

/**
 * Porta de saída: emite o token de acesso de um usuário autenticado. Implementada na camada de
 * infraestrutura (JWT); o caso de uso não conhece o formato do token.
 */
public interface EmissorDeToken {

    TokenEmitido emitir(Usuario usuario);

    record TokenEmitido(String token, Instant expiraEm) {
    }
}
