package br.senac.qs.recursos.domain.repository;

import java.util.Optional;

import br.senac.qs.recursos.domain.model.Competencia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetenciaRepository extends JpaRepository<Competencia, Long> {

    Optional<Competencia> findByNomeIgnoreCase(String nome);
}
