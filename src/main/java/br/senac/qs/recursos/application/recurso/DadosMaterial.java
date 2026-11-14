package br.senac.qs.recursos.application.recurso;

import br.senac.qs.recursos.domain.model.EstadoConservacao;

public record DadosMaterial(String nome, boolean restrito, String patrimonio, String tipoMaterial,
                            EstadoConservacao estadoConservacao) {
}
