package br.senac.qs.recursos.domain.repository;

import java.util.List;
import java.util.Optional;

import br.senac.qs.recursos.domain.model.MovimentacaoMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** A ordem das movimentações é a do id (sequência do banco): a de maior id é a mais recente. */
public interface MovimentacaoMaterialRepository extends JpaRepository<MovimentacaoMaterial, Long> {

    /** Movimentação mais recente do item, em qualquer reserva. Se for retirada, o item está com alguém. */
    Optional<MovimentacaoMaterial> findFirstByMaterialIdOrderByIdDesc(Long materialId);

    List<MovimentacaoMaterial> findByMaterialIdOrderByIdAsc(Long materialId);

    /** Itens retirados e ainda não devolvidos: retiradas sem movimentação posterior do mesmo item. */
    @Query("""
            select m from MovimentacaoMaterial m
              join fetch m.material
              join fetch m.reserva r
              join fetch r.solicitante
            where m.tipo = br.senac.qs.recursos.domain.model.TipoMovimentacao.RETIRADA
              and not exists (select d.id from MovimentacaoMaterial d where d.material = m.material and d.id > m.id)
            order by m.id""")
    List<MovimentacaoMaterial> pendentesDeDevolucao();
}
