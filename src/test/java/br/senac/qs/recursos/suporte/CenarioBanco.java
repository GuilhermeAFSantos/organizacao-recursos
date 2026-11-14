package br.senac.qs.recursos.suporte;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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

    /** Cria um usuário ativo com senha de verdade (hash BCrypt), para testes de login. */
    public String criarUsuarioComSenha(String perfil, String senha) {
        String email = perfil.toLowerCase() + "-" + UUID.randomUUID() + "@senac.br";
        jdbc.update("INSERT INTO usuario (nome, email, senha_hash, perfil) VALUES (?, ?, ?, ?)",
                "Usuário de teste", email, new BCryptPasswordEncoder().encode(senha), perfil);
        return email;
    }

    public void desativarUsuario(String email) {
        jdbc.update("UPDATE usuario SET ativo = false WHERE email = ?", email);
    }

    public long criarSala() {
        return criarSala(false);
    }

    public long criarSala(boolean restrita) {
        long id = jdbc.queryForObject("INSERT INTO recurso (tipo, nome, restrito) VALUES ('SALA', ?, ?) RETURNING id",
                Long.class, "Sala " + UUID.randomUUID().toString().substring(0, 8), restrita);
        jdbc.update("INSERT INTO sala (id, capacidade, bloco, andar, tipo_sala) VALUES (?, 40, 'A', 1, 'AULA')", id);
        return id;
    }

    /** Sala com bloco e nome escolhidos pelo teste (o bloco único isola a pesquisa dos dados de outros testes). */
    public long criarSala(boolean restrita, String bloco, String nome) {
        long id = jdbc.queryForObject("INSERT INTO recurso (tipo, nome, restrito) VALUES ('SALA', ?, ?) RETURNING id",
                Long.class, nome, restrita);
        jdbc.update("INSERT INTO sala (id, capacidade, bloco, andar, tipo_sala) VALUES (?, 30, ?, 1, 'LABORATORIO')",
                id, bloco);
        return id;
    }

    public int contarReservas(long salaId) {
        return jdbc.queryForObject("""
                SELECT count(DISTINCT reserva_id) FROM ocupacao WHERE recurso_id = ? AND reserva_id IS NOT NULL""",
                Integer.class, salaId);
    }

    /** Auditoria da reserva em ordem cronológica, no formato "ANTERIOR→NOVO por autor". */
    public java.util.List<String> auditoriaDaReserva(long reservaId) {
        return jdbc.queryForList("""
                SELECT coalesce(estado_anterior, '—') || '→' || estado_novo || ' por ' || usuario_email
                FROM registro_auditoria WHERE entidade = 'RESERVA' AND entidade_id = ? ORDER BY id""",
                String.class, reservaId);
    }

    public int contarConflitosEvitados(long recursoId) {
        return jdbc.queryForObject("SELECT count(*) FROM conflito_evitado WHERE recurso_id = ?", Integer.class,
                recursoId);
    }

    public int contarOcupacoesBloqueantes(long salaId) {
        return jdbc.queryForObject("SELECT count(*) FROM ocupacao WHERE recurso_id = ? AND bloqueante",
                Integer.class, salaId);
    }
}
