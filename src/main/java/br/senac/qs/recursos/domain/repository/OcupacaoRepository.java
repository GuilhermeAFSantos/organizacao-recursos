package br.senac.qs.recursos.domain.repository;

import java.time.Instant;

import br.senac.qs.recursos.domain.model.Ocupacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OcupacaoRepository extends JpaRepository<Ocupacao, Long> {

    @Query("""
            select count(o) > 0 from Ocupacao o
            where o.recurso.id = :recursoId
              and o.bloqueante = true
              and o.periodo.inicio < :fim
              and o.periodo.fim > :inicio""")
    boolean existeSobreposicao(Long recursoId, Instant inicio, Instant fim);
}
