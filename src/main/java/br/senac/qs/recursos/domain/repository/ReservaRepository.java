package br.senac.qs.recursos.domain.repository;

import br.senac.qs.recursos.domain.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
}
