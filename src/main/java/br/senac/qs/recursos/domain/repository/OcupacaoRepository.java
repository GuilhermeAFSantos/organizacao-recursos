package br.senac.qs.recursos.domain.repository;

import java.time.Instant;

import br.senac.qs.recursos.domain.model.Ocupacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OcupacaoRepository extends JpaRepository<Ocupacao, Long> {

    /**
     * Existe ocupação bloqueante (reserva ativa ou manutenção) do recurso que se sobrepõe a
     * {@code [inicio, fim)}? Mesma regra de {@code PeriodoReserva.sobrepoe} e do operador {@code &&}
     * da constraint: cada intervalo começa antes de o outro terminar (intervalos que se tocam não contam).
     */
    @Query("""
            select count(o) > 0 from Ocupacao o
            where o.recurso.id = :recursoId
              and o.bloqueante = true
              and o.periodo.inicio < :fim
              and o.periodo.fim > :inicio""")
    boolean existeSobreposicao(Long recursoId, Instant inicio, Instant fim);
}
