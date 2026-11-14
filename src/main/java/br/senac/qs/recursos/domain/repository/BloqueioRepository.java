package br.senac.qs.recursos.domain.repository;

import br.senac.qs.recursos.domain.model.Bloqueio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BloqueioRepository extends JpaRepository<Bloqueio, Long> {
}
