package br.senac.qs.recursos.domain.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import br.senac.qs.recursos.domain.model.Competencia;
import br.senac.qs.recursos.domain.model.EstadoConservacao;
import br.senac.qs.recursos.domain.model.Material;
import br.senac.qs.recursos.domain.model.Ocupacao;
import br.senac.qs.recursos.domain.model.PeriodoReserva;
import br.senac.qs.recursos.domain.model.Professor;
import br.senac.qs.recursos.domain.model.Recurso;
import br.senac.qs.recursos.domain.model.Sala;
import br.senac.qs.recursos.domain.model.TipoRecurso;
import br.senac.qs.recursos.domain.pesquisa.FiltroRecursos;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Traduz um {@link FiltroRecursos} em consulta (RF03).
 *
 * <p>Cada filtro informado vira "o id do recurso está (ou não está) numa subconsulta". Assim, um
 * filtro de sala nunca afeta professores por acidente, os parâmetros são sempre ligados (sem SQL
 * concatenado) e filtro ausente simplesmente não entra na consulta.
 */
public final class PesquisaDeRecursos {

    private static final String ID = "id";

    private PesquisaDeRecursos() {
    }

    public static Specification<Recurso> para(FiltroRecursos filtro) {
        return (recurso, consulta, cb) -> {
            Expression<Long> id = recurso.get(ID);
            var condicoes = new ArrayList<Predicate>();
            condicoes.add(cb.isTrue(recurso.get("ativo")));
            if (filtro.tipo() != null) {
                condicoes.add(id.in(idsDoTipo(filtro.tipo(), consulta)));
            }
            if (filtro.filtraSala()) {
                condicoes.add(id.in(salas(filtro, consulta, cb)));
            }
            if (filtro.competencia() != null) {
                condicoes.add(id.in(professoresCom(filtro.competencia(), consulta, cb)));
            }
            if (filtro.tipoMaterial() != null) {
                condicoes.add(id.in(materiaisDoTipo(filtro.tipoMaterial(), consulta, cb)));
            }
            if (filtro.periodo() != null) {
                condicoes.add(cb.not(id.in(ocupadosEm(filtro.periodo(), consulta, cb))));
                condicoes.add(cb.not(id.in(materiaisDanificados(consulta, cb))));
            }
            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }

    private static Subquery<Long> idsDoTipo(TipoRecurso tipo, CriteriaQuery<?> consulta) {
        var classe = switch (tipo) {
            case SALA -> Sala.class;
            case PROFESSOR -> Professor.class;
            case MATERIAL -> Material.class;
        };
        var sub = consulta.subquery(Long.class);
        return sub.select(sub.from(classe).get(ID));
    }

    private static Subquery<Long> salas(FiltroRecursos filtro, CriteriaQuery<?> consulta, CriteriaBuilder cb) {
        var sub = consulta.subquery(Long.class);
        var sala = sub.from(Sala.class);
        List<Predicate> condicoes = new ArrayList<>();
        if (filtro.capacidadeMinima() != null) {
            condicoes.add(cb.greaterThanOrEqualTo(sala.<Integer>get("capacidade"), filtro.capacidadeMinima()));
        }
        if (filtro.bloco() != null) {
            condicoes.add(cb.equal(cb.lower(sala.get("bloco")), filtro.bloco().toLowerCase(Locale.ROOT)));
        }
        if (filtro.andar() != null) {
            condicoes.add(cb.equal(sala.get("andar"), filtro.andar()));
        }
        if (filtro.tipoSala() != null) {
            condicoes.add(cb.equal(sala.get("tipoSala"), filtro.tipoSala()));
        }
        return sub.select(sala.<Long>get(ID)).where(condicoes.toArray(Predicate[]::new));
    }

    /** Professores que têm a competência, comparando como o cadastro compara ("java" = " Java "). */
    private static Subquery<Long> professoresCom(String competencia, CriteriaQuery<?> consulta, CriteriaBuilder cb) {
        var sub = consulta.subquery(Long.class);
        var professor = sub.from(Professor.class);
        var comp = professor.join("competencias");
        return sub.select(professor.get(ID))
                .where(cb.equal(cb.lower(comp.get("nome")), Competencia.chave(competencia)));
    }

    private static Subquery<Long> materiaisDoTipo(String tipo, CriteriaQuery<?> consulta, CriteriaBuilder cb) {
        var sub = consulta.subquery(Long.class);
        var material = sub.from(Material.class);
        return sub.select(material.get(ID))
                .where(cb.equal(cb.lower(material.get("tipoMaterial")), tipo.toLowerCase(Locale.ROOT)));
    }

    /**
     * Recursos com ocupação bloqueante (reserva ativa ou bloqueio) que se sobrepõe ao período: a
     * mesma regra de {@code PeriodoReserva.sobrepoe} e da constraint do banco (encostar não conta).
     */
    private static Subquery<Long> ocupadosEm(PeriodoReserva periodo, CriteriaQuery<?> consulta, CriteriaBuilder cb) {
        var sub = consulta.subquery(Long.class);
        var ocupacao = sub.from(Ocupacao.class);
        return sub.select(ocupacao.get("recurso").get(ID)).where(
                cb.isTrue(ocupacao.get("bloqueante")),
                cb.lessThan(ocupacao.get("periodo").<Instant>get("inicio"), periodo.fim()),
                cb.greaterThan(ocupacao.get("periodo").<Instant>get("fim"), periodo.inicio()));
    }

    private static Subquery<Long> materiaisDanificados(CriteriaQuery<?> consulta, CriteriaBuilder cb) {
        var sub = consulta.subquery(Long.class);
        var material = sub.from(Material.class);
        return sub.select(material.get(ID))
                .where(cb.equal(material.get("estadoConservacao"), EstadoConservacao.DANIFICADO));
    }
}
