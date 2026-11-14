package br.senac.qs.recursos.suporte;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Monta dados de teste direto no banco (fora da API sob teste).
 *
 * <p>Reservas não podem ser apagadas (trigger da RC07), então os testes não limpam o banco entre
 * si. Em vez disso, cada teste cria a PRÓPRIA sala e os PRÓPRIOS usuários, com nomes únicos. Assim
 * os testes ficam isolados e podem rodar em qualquer ordem.
 */
public class CenarioBanco {

    private final JdbcTemplate jdbc;

    public CenarioBanco(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Cria um usuário ativo e devolve o e-mail (que é o "subject" do JWT). */
    public String criarUsuario(String perfil) {
        String email = perfil.toLowerCase() + "-" + UUID.randomUUID() + "@senac.br";
        jdbc.update("INSERT INTO usuario (nome, email, senha_hash, perfil) VALUES (?, ?, 'nao-usado', ?)",
                "Usuário de teste", email, perfil);
        return email;
    }

    public long criarSala() {
        long id = jdbc.queryForObject("INSERT INTO recurso (tipo, nome) VALUES ('SALA', ?) RETURNING id",
                Long.class, "Sala " + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("INSERT INTO sala (id, capacidade, bloco, andar, tipo_sala) VALUES (?, 40, 'A', 1, 'AULA')", id);
        return id;
    }

    public int contarReservas(long salaId) {
        return jdbc.queryForObject("""
                SELECT count(DISTINCT reserva_id) FROM ocupacao WHERE recurso_id = ? AND reserva_id IS NOT NULL""",
                Integer.class, salaId);
    }

    public int contarOcupacoesBloqueantes(long salaId) {
        return jdbc.queryForObject("SELECT count(*) FROM ocupacao WHERE recurso_id = ? AND bloqueante",
                Integer.class, salaId);
    }
}
