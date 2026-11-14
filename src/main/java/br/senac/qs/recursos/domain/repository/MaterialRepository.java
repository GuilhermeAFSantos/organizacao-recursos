package br.senac.qs.recursos.domain.repository;

import br.senac.qs.recursos.domain.model.Material;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    boolean existsByPatrimonio(String patrimonio);

    boolean existsByPatrimonioAndIdNot(String patrimonio, Long id);
}
