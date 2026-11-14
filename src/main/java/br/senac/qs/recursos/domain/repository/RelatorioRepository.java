package br.senac.qs.recursos.domain.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import br.senac.qs.recursos.domain.model.Reserva;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Consultas de agregação do relatório de utilização (RF12), em SQL nativo: soma de intervalos
 * recortados ao período ({@code least}/{@code greatest}) é natural no PostgreSQL e cara em JPQL.
 *
 * <p>"Ocupa" = APROVADA, EM_USO ou CONCLUIDA. Aguardando aprovação, rejeitada e cancelada não são
 * utilização; o não comparecimento é contado à parte e não soma horas.
 */
public interface RelatorioRepository extends Repository<Reserva, Long> {

    String NOME_EXIBIDO = "CASE WHEN m.patrimonio IS NULL THEN r.nome ELSE r.nome || ' (' || m.patrimonio || ')' END";

    interface UtilizacaoDoRecurso {
        Long getRecursoId();

        String getNome();

        String getTipo();

        Long getReservas();

        BigDecimal getSegundos();

        Long getNaoComparecimentos();
    }

    interface TotaisDeUtilizacao {
        Long getReservas();

        BigDecimal getSegundos();

        Long getNaoComparecimentos();
    }

    interface ConflitosDoRecurso {
        Long getRecursoId();

        String getNome();

        String getTipo();

        String getOrigem();

        Long getQuantidade();
    }

    /** Por recurso, com as horas de cada ocupação recortadas a [de, ate). */
    @Query(value = """
            SELECT o.recurso_id AS recursoId, """ + NOME_EXIBIDO + """
             AS nome, r.tipo AS tipo,
                   count(DISTINCT res.id) FILTER (WHERE res.status IN ('APROVADA', 'EM_USO', 'CONCLUIDA')) AS reservas,
                   coalesce(sum(extract(epoch FROM least(o.fim, :ate) - greatest(o.inicio, :de)))
                            FILTER (WHERE res.status IN ('APROVADA', 'EM_USO', 'CONCLUIDA')), 0) AS segundos,
                   count(DISTINCT res.id) FILTER (WHERE res.status = 'NAO_COMPARECEU') AS naoComparecimentos
            FROM ocupacao o
            JOIN reserva res ON res.id = o.reserva_id
            JOIN recurso r ON r.id = o.recurso_id
            LEFT JOIN material m ON m.id = r.id
            WHERE o.inicio < :ate AND o.fim > :de
              AND res.status IN ('APROVADA', 'EM_USO', 'CONCLUIDA', 'NAO_COMPARECEU')
            GROUP BY o.recurso_id, r.nome, r.tipo, m.patrimonio""", nativeQuery = true)
    List<UtilizacaoDoRecurso> utilizacaoPorRecurso(Instant de, Instant ate);

    /** Por reserva distinta: uma reserva de sala + professor conta uma vez, com as horas dela. */
    @Query(value = """
            SELECT count(*) FILTER (WHERE status IN ('APROVADA', 'EM_USO', 'CONCLUIDA')) AS reservas,
                   coalesce(sum(extract(epoch FROM least(fim, :ate) - greatest(inicio, :de)))
                            FILTER (WHERE status IN ('APROVADA', 'EM_USO', 'CONCLUIDA')), 0) AS segundos,
                   count(*) FILTER (WHERE status = 'NAO_COMPARECEU') AS naoComparecimentos
            FROM reserva
            WHERE inicio < :ate AND fim > :de""", nativeQuery = true)
    TotaisDeUtilizacao totais(Instant de, Instant ate);

    /** Conflitos evitados cujo horário PEDIDO cai no período, por recurso (nulo se desconhecido) e origem. */
    @Query(value = """
            SELECT c.recurso_id AS recursoId, """ + NOME_EXIBIDO + """
             AS nome, r.tipo AS tipo, c.origem AS origem, count(*) AS quantidade
            FROM conflito_evitado c
            LEFT JOIN recurso r ON r.id = c.recurso_id
            LEFT JOIN material m ON m.id = r.id
            WHERE c.inicio < :ate AND c.fim > :de
            GROUP BY c.recurso_id, r.nome, r.tipo, m.patrimonio, c.origem""", nativeQuery = true)
    List<ConflitosDoRecurso> conflitosEvitados(Instant de, Instant ate);
}
