package br.senac.qs.recursos.domain.model;

/**
 * Quem barrou a sobreposição: a pré-checagem da aplicação (caso normal, com mensagem detalhada) ou
 * a constraint de exclusão do banco (corrida que escapou da pré-checagem). Se a origem BANCO
 * aparecer no relatório, o lock dos recursos deixou passar algo e vale investigar.
 */
public enum OrigemConflito {
    APLICACAO, BANCO
}
