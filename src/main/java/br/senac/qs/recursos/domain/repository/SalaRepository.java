package br.senac.qs.recursos.domain.repository;

import java.util.Optional;

import br.senac.qs.recursos.domain.model.Sala;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SalaRepository extends JpaRepository<Sala, Long> {

    /**
     * Busca a sala TRAVANDO a linha até o fim da transação ({@code SELECT ... FOR NO KEY UPDATE}).
     *
     * <p>Serializa as reservas do mesmo recurso: uma segunda transação espera aqui até a primeira
     * terminar. Sem isso, inserções conflitantes simultâneas chegam juntas à constraint de exclusão,
     * que pode entrar em deadlock (SQLSTATE 40P01). Reservas com vários recursos deverão travá-los
     * sempre na mesma ordem (por id), para não criar outro deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sala s where s.id = :id")
    Optional<Sala> travarParaReserva(Long id);
}
