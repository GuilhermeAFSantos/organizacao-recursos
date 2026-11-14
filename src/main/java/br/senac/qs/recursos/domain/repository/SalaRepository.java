package br.senac.qs.recursos.domain.repository;

import br.senac.qs.recursos.domain.model.Sala;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaRepository extends JpaRepository<Sala, Long> {
}
