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

    /**
     * Reservas ativas (solicitadas, aprovadas ou em uso) do recurso que ainda não terminaram. Enquanto
     * houver alguma, o recurso não pode ser desativado: quem reservou ficaria sem aviso.
     */
    @Query("""
            select count(distinct o.reserva.id) from Ocupacao o
            where o.recurso.id = :recursoId
              and o.bloqueante = true
              and o.reserva.status in (br.senac.qs.recursos.domain.model.StatusReserva.SOLICITADA,
                                       br.senac.qs.recursos.domain.model.StatusReserva.APROVADA,
                                       br.senac.qs.recursos.domain.model.StatusReserva.EM_USO)
              and o.periodo.fim > :agora""")
    long contarReservasAtivasNaoTerminadas(Long recursoId, Instant agora);
}
