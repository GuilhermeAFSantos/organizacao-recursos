package br.senac.qs.recursos.application.recurso;

import br.senac.qs.recursos.domain.model.TipoSala;

public record DadosSala(String nome, boolean restrito, int capacidade, String bloco, int andar, TipoSala tipoSala) {
}
