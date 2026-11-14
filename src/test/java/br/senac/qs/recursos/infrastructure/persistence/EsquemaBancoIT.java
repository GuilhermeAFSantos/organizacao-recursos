package br.senac.qs.recursos.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;

import br.senac.qs.recursos.suporte.PostgresTestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prova que as garantias do SCHEMA funcionam no PostgreSQL real (Testcontainers), sem depender
 * do código Java: mesmo um INSERT manual ou um bug futuro na aplicação é barrado pelo banco.
 *
 * <p>Cada teste roda numa transação desfeita ao final ({@code @JdbcTest}), então os dados de um
 * teste não interferem nos outros.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Schema do banco (Flyway V1) em PostgreSQL real")
class EsquemaBancoIT {

    private static final String SQLSTATE_EXCLUSAO = "23P01";
    private static final String SQLSTATE_CHECK = "23514";
    private static final String DIA = "2026-11-20T";
    private static final String FUSO = ":00:00-03:00";

    @Autowired
    private JdbcTemplate jdbc;

    private long usuarioId;
    private long salaA;
    private long salaB;

    @BeforeEach
    void criarDadosBase() {
        usuarioId = jdbc.queryForObject("""
                INSERT INTO usuario (nome, email, senha_hash, perfil)
                VALUES ('Ana', 'ana@senac.br', 'hash', 'SOLICITANTE') RETURNING id""", Long.class);
        salaA = criarRecurso("Sala A");
        salaB = criarRecurso("Sala B");
    }

    @Test
    @DisplayName("extensão btree_gist instalada pela migration")
    void extensaoInstalada() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class))
                .isEqualTo(1);
    }

    @Nested
    @DisplayName("RC02/RC03: constraint de exclusão na tabela ocupacao")
    class ExclusaoDeSobreposicao {

        @ParameterizedTest(name = "[{0}h-{1}h) x [{2}h-{3}h) -> conflita={4}")
        @CsvSource({
            // inicioA, fimA, inicioB, fimB, conflita
            " 8, 10, 10, 12, false",  // B começa exatamente quando A termina: só se tocam
            "10, 12,  8, 10, false",  // A começa exatamente quando B termina: só se tocam
            " 8, 10, 12, 14, false",  // totalmente separados
            " 8, 10,  9, 11, true",   // sobreposição parcial no fim de A
            " 9, 11,  8, 10, true",   // sobreposição parcial no início de A
            " 8, 12,  9, 10, true",   // B contido em A
            " 9, 10,  8, 12, true",   // A contido em B
            " 8, 10,  8, 10, true"    // intervalos idênticos
        })
        @DisplayName("mesmo recurso: sobreposição é barrada; intervalos que só se tocam são aceitos")
        void sobreposicaoNoMesmoRecurso(int inicioA, int fimA, int inicioB, int fimB, boolean conflita) {
            ocupar(salaA, criarReserva(inicioA, fimA), inicioA, fimA, true);
            long reservaB = criarReserva(inicioB, fimB);

            if (conflita) {
                assertThatThrownBy(() -> ocupar(salaA, reservaB, inicioB, fimB, true))
                        .satisfies(e -> assertThat(sqlState(e)).isEqualTo(SQLSTATE_EXCLUSAO));
            } else {
                ocupar(salaA, reservaB, inicioB, fimB, true);
                assertThat(contarOcupacoes(salaA)).isEqualTo(2);
            }
        }

        @Test
        @DisplayName("recursos diferentes no mesmo horário não conflitam")
        void recursosDiferentes() {
            long reserva = criarReserva(8, 10);
            ocupar(salaA, reserva, 8, 10, true);
            ocupar(salaB, reserva, 8, 10, true);
            assertThat(contarOcupacoes(salaA) + contarOcupacoes(salaB)).isEqualTo(2);
        }

        @Test
        @DisplayName("ocupação NÃO bloqueante (reserva cancelada/rejeitada) libera o horário")
        void naoBloqueanteLiberaHorario() {
            ocupar(salaA, criarReserva(8, 10), 8, 10, false);
            ocupar(salaA, criarReserva(8, 10), 8, 10, true);
            assertThat(contarOcupacoes(salaA)).isEqualTo(2);
        }

        @Test
        @DisplayName("RC04: reserva sobre período de manutenção é barrada pelo banco")
        void manutencaoBloqueiaReserva() {
            long bloqueio = jdbc.queryForObject("""
                    INSERT INTO bloqueio (tipo, motivo, inicio, fim, criado_por_id)
                    VALUES ('MANUTENCAO', 'Troca do projetor', ?, ?, ?) RETURNING id""",
                    Long.class, hora(7), hora(12), usuarioId);
            jdbc.update("INSERT INTO ocupacao (recurso_id, bloqueio_id, inicio, fim) VALUES (?, ?, ?, ?)",
                    salaA, bloqueio, hora(7), hora(12));

            long reserva = criarReserva(9, 10);
            assertThatThrownBy(() -> ocupar(salaA, reserva, 9, 10, true))
                    .satisfies(e -> assertThat(sqlState(e)).isEqualTo(SQLSTATE_EXCLUSAO));
        }

        @Test
        @DisplayName("ocupação precisa de exatamente uma origem (reserva OU bloqueio)")
        void origemObrigatoria() {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO ocupacao (recurso_id, inicio, fim) VALUES (?, ?, ?)", salaA, hora(8), hora(9)))
                    .satisfies(e -> assertThat(sqlState(e)).isEqualTo(SQLSTATE_CHECK));
        }
    }

    @Nested
    @DisplayName("RC01: término posterior ao início (segunda barreira, no banco)")
    class PeriodoValido {

        @ParameterizedTest(name = "inicio={0}h, fim={1}h é rejeitado")
        @CsvSource({
            "10, 10",  // duração zero (término igual ao início)
            "10,  9"   // término antes do início
        })
        void rejeitaPeriodoInvalido(int inicio, int fim) {
            assertThatThrownBy(() -> criarReserva(inicio, fim))
                    .satisfies(e -> assertThat(sqlState(e)).isEqualTo(SQLSTATE_CHECK));
        }
    }

    @Nested
    @DisplayName("RC08: auditoria somente inserção")
    class AuditoriaImutavel {

        private long registro;

        @BeforeEach
        void inserirRegistro() {
            registro = jdbc.queryForObject("""
                    INSERT INTO registro_auditoria (entidade, entidade_id, acao, estado_anterior, estado_novo, motivo, usuario_id)
                    VALUES ('RESERVA', 1, 'TRANSICAO', 'SOLICITADA', 'APROVADA', 'ok', ?) RETURNING id""",
                    Long.class, usuarioId);
        }

        @Test
        @DisplayName("INSERT é permitido")
        void inserePermitido() {
            assertThat(jdbc.queryForObject("SELECT estado_novo FROM registro_auditoria WHERE id = ?", String.class, registro))
                    .isEqualTo("APROVADA");
        }

        @Test
        @DisplayName("UPDATE é bloqueado por trigger")
        void updateBloqueado() {
            assertThatThrownBy(() -> jdbc.update("UPDATE registro_auditoria SET estado_novo = 'REJEITADA' WHERE id = ?", registro))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("somente inserção");
        }

        @Test
        @DisplayName("DELETE é bloqueado por trigger")
        void deleteBloqueado() {
            assertThatThrownBy(() -> jdbc.update("DELETE FROM registro_auditoria WHERE id = ?", registro))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("somente inserção");
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private long criarRecurso(String nome) {
        long id = jdbc.queryForObject("INSERT INTO recurso (tipo, nome) VALUES ('SALA', ?) RETURNING id", Long.class, nome);
        jdbc.update("INSERT INTO sala (id, capacidade, bloco, andar, tipo_sala) VALUES (?, 40, 'A', 1, 'AULA')", id);
        return id;
    }

    private long criarReserva(int inicio, int fim) {
        return jdbc.queryForObject("""
                INSERT INTO reserva (solicitante_id, inicio, fim, finalidade, status)
                VALUES (?, ?, ?, 'Aula', 'SOLICITADA') RETURNING id""",
                Long.class, usuarioId, hora(inicio), hora(fim));
    }

    private void ocupar(long recurso, long reserva, int inicio, int fim, boolean bloqueante) {
        jdbc.update("INSERT INTO ocupacao (recurso_id, reserva_id, inicio, fim, bloqueante) VALUES (?, ?, ?, ?, ?)",
                recurso, reserva, hora(inicio), hora(fim), bloqueante);
    }

    private int contarOcupacoes(long recurso) {
        return jdbc.queryForObject("SELECT count(*) FROM ocupacao WHERE recurso_id = ?", Integer.class, recurso);
    }

    private static OffsetDateTime hora(int hora) {
        return OffsetDateTime.parse(DIA + "%02d".formatted(hora) + FUSO);
    }

    private static String sqlState(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
