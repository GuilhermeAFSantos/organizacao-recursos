package br.senac.qs.recursos.application;

import java.sql.SQLException;

/**
 * Reconhece a recusa da constraint de exclusão do PostgreSQL (SQLSTATE 23P01), que garante que um
 * recurso não tenha duas ocupações bloqueantes sobrepostas (RC02/RC03/RC04).
 */
public final class ViolacaoDeExclusao {

    /** SQLSTATE do PostgreSQL para {@code exclusion_violation}. */
    public static final String SQLSTATE = "23P01";

    private ViolacaoDeExclusao() {
    }

    public static boolean causou(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql) {
                return SQLSTATE.equals(sql.getSQLState());
            }
        }
        return false;
    }
}
