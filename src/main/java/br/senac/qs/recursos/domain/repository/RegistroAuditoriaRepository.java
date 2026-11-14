package br.senac.qs.recursos.domain.repository;

import br.senac.qs.recursos.domain.model.RegistroAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {
}
