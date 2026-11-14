package br.senac.qs.recursos.domain.repository;

import java.time.Instant;
import java.util.List;

import br.senac.qs.recursos.domain.model.Ocupacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OcupacaoRepository extends JpaRepository<Ocupacao, Long> {

    /**
     * Ocupações bloqueantes (reservas ativas ou bloqueios) do recurso que se sobrepõem a
     * {@code [inicio, fim)}. Mesma regra de {@code PeriodoReserva.sobrepoe} e do operador {@code &&}
     * da constraint: cada intervalo começa antes de o outro terminar (intervalos que se tocam não contam).
     */
    @Query("""
            select o from Ocupacao o left join fetch o.bloqueio
            where o.recurso.id = :recursoId
              and o.bloqueante = true
              and o.periodo.inicio < :fim
              and o.periodo.fim > :inicio
            order by o.periodo.inicio""")
    List<Ocupacao> sobrepostas(Long recursoId, Instant inicio, Instant fim);
}
