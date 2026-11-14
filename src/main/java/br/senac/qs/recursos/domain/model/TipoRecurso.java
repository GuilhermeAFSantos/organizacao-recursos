package br.senac.qs.recursos.domain.model;

/**
 * Os três tipos de recurso reserváveis (coluna {@code recurso.tipo}), com o necessário para as
 * mensagens ao usuário saírem no gênero certo ("A sala ... está ocupada", "O professor ... está ocupado").
 */
public enum TipoRecurso {
    SALA("sala", true),
    PROFESSOR("professor", false),
    MATERIAL("material", false);

    private final String rotulo;
    private final boolean feminino;

    TipoRecurso(String rotulo, boolean feminino) {
        this.rotulo = rotulo;
        this.feminino = feminino;
    }

    /** Ex.: "A sala Sala 101", "O professor Carlos Lima". */
    public String identificar(String nome) {
        return (feminino ? "A " : "O ") + rotulo + " " + nome;
    }

    /** Concorda um particípio no masculino com o tipo: "ocupado" vira "ocupada" para salas. */
    public String concordar(String masculino) {
        return feminino ? masculino.substring(0, masculino.length() - 1) + "a" : masculino;
    }

    /** Ex.: "outra sala", "outro professor". */
    public String outro() {
        return (feminino ? "outra " : "outro ") + rotulo;
    }
}
