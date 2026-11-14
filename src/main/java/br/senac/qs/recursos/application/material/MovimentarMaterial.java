package br.senac.qs.recursos.application.material;

import java.time.Clock;
import java.util.List;

import br.senac.qs.recursos.application.UsuarioDoToken;
import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.model.EstadoConservacao;
import br.senac.qs.recursos.domain.model.Material;
import br.senac.qs.recursos.domain.model.MovimentacaoMaterial;
import br.senac.qs.recursos.domain.model.Reserva;
import br.senac.qs.recursos.domain.repository.MaterialRepository;
import br.senac.qs.recursos.domain.repository.MovimentacaoMaterialRepository;
import br.senac.qs.recursos.domain.repository.RecursoRepository;
import br.senac.qs.recursos.domain.repository.ReservaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retirada e devolução de materiais (RF09), registradas no balcão por um RESPONSAVEL ou
 * ADMINISTRADOR. Quem responde pelo item é o solicitante da reserva.
 *
 * <p>Concorrência: o item é travado ({@link RecursoRepository#travar}) antes de ler a última
 * movimentação. Duas retiradas simultâneas do mesmo item se enfileiram, e a segunda já enxerga a
 * primeira (e é recusada).
 */
@Service
public class MovimentarMaterial {

    private final UsuarioDoToken usuarioDoToken;
    private final ReservaRepository reservas;
    private final RecursoRepository recursos;
    private final MaterialRepository materiais;
    private final MovimentacaoMaterialRepository movimentacoes;
    private final Clock clock;

    public MovimentarMaterial(UsuarioDoToken usuarioDoToken, ReservaRepository reservas, RecursoRepository recursos,
                              MaterialRepository materiais, MovimentacaoMaterialRepository movimentacoes, Clock clock) {
        this.usuarioDoToken = usuarioDoToken;
        this.reservas = reservas;
        this.recursos = recursos;
        this.materiais = materiais;
        this.movimentacoes = movimentacoes;
        this.clock = clock;
    }

    @Transactional
    public MovimentacaoResumo retirar(Long reservaId, Long materialId, String emailAutor, String observacao) {
        var autor = usuarioDoToken.ativo(emailAutor);
        var reserva = reserva(reservaId);
        var material = materialTravado(materialId);
        var ultima = ultimaMovimentacao(materialId);
        var retirada = MovimentacaoMaterial.retirar(reserva, material, autor, ultima, observacao, clock.instant());
        return MovimentacaoResumo.de(movimentacoes.save(retirada));
    }

    /** O novo estado do material é gravado junto, na mesma transação (o material está gerenciado). */
    @Transactional
    public MovimentacaoResumo devolver(Long reservaId, Long materialId, String emailAutor, EstadoConservacao estado,
                                       String observacao) {
        var autor = usuarioDoToken.ativo(emailAutor);
        var reserva = reserva(reservaId);
        var material = materialTravado(materialId);
        var ultima = ultimaMovimentacao(materialId);
        var devolucao = MovimentacaoMaterial.devolver(reserva, material, autor, ultima, estado, observacao,
                clock.instant());
        return MovimentacaoResumo.de(movimentacoes.save(devolucao));
    }

    @Transactional(readOnly = true)
    public List<PendenciaResumo> pendentes() {
        var agora = clock.instant();
        return movimentacoes.pendentesDeDevolucao().stream().map(m -> {
            var fim = m.getReserva().getPeriodo().fim();
            return new PendenciaResumo(m.getMaterial().getId(), m.getMaterial().getNomeParaExibicao(),
                    m.getReserva().getId(), m.getReserva().getSolicitante().getEmail(), m.getDataHora(), fim,
                    agora.isAfter(fim));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoResumo> historico(Long materialId) {
        if (!materiais.existsById(materialId)) {
            throw materialNaoEncontrado(materialId);
        }
        return movimentacoes.findByMaterialIdOrderByIdAsc(materialId).stream().map(MovimentacaoResumo::de).toList();
    }

    private Reserva reserva(Long reservaId) {
        return reservas.findById(reservaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reserva " + reservaId + " não encontrada."));
    }

    private Material materialTravado(Long materialId) {
        recursos.travar(materialId);
        return materiais.findById(materialId).orElseThrow(() -> materialNaoEncontrado(materialId));
    }

    private MovimentacaoMaterial ultimaMovimentacao(Long materialId) {
        return movimentacoes.findFirstByMaterialIdOrderByIdDesc(materialId).orElse(null);
    }

    private static RecursoNaoEncontradoException materialNaoEncontrado(Long materialId) {
        return new RecursoNaoEncontradoException("Material " + materialId + " não encontrado.");
    }
}
