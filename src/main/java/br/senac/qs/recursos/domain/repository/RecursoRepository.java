package br.senac.qs.recursos.domain.repository;

import java.util.Optional;

import br.senac.qs.recursos.domain.model.Recurso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface RecursoRepository extends JpaRepository<Recurso, Long>, JpaSpecificationExecutor<Recurso> {

    /**
     * Trava a linha do recurso até o fim da transação ({@code SELECT ... FOR NO KEY UPDATE}).
     *
     * <p>Ponto ÚNICO de serialização de tudo que ocupa o recurso: reservas e bloqueios travam a
     * mesma linha, então uma operação espera a outra terminar e a pré-checagem enxerga o que foi
     * confirmado. Sem isso, inserções conflitantes simultâneas podem entrar em deadlock na
     * constraint de exclusão (SQLSTATE 40P01, ver docs/arquitetura.md §3.1). Consulta nativa de
     * propósito: trava exatamente a linha de {@code recurso}, independentemente do tipo do recurso.
     */
    @Query(value = "SELECT id FROM recurso WHERE id = :id AND ativo FOR NO KEY UPDATE", nativeQuery = true)
    Optional<Long> travar(Long id);
}
