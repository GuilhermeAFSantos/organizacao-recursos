package br.senac.qs.recursos.domain.repository;

import java.time.Instant;
import java.util.ArrayList;

import br.senac.qs.recursos.domain.model.Reserva;
import br.senac.qs.recursos.domain.pesquisa.FiltroReservas;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/** Traduz um {@link FiltroReservas} em consulta: só os filtros informados entram, com parâmetros ligados. */
public final class PesquisaDeReservas {

    private PesquisaDeReservas() {
    }

    public static Specification<Reserva> para(FiltroReservas filtro) {
        return (reserva, consulta, cb) -> {
            var condicoes = new ArrayList<Predicate>();
            if (filtro.solicitanteId() != null) {
                condicoes.add(cb.equal(reserva.get("solicitante").get("id"), filtro.solicitanteId()));
            }
            if (filtro.status() != null) {
                condicoes.add(cb.equal(reserva.get("status"), filtro.status()));
            }
            if (filtro.de() != null) {
                condicoes.add(cb.greaterThan(reserva.get("periodo").<Instant>get("fim"), filtro.de()));
            }
            if (filtro.ate() != null) {
                condicoes.add(cb.lessThan(reserva.get("periodo").<Instant>get("inicio"), filtro.ate()));
            }
            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }
}
