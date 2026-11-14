package br.senac.qs.recursos.web.material;

import java.util.List;

import br.senac.qs.recursos.application.material.MovimentacaoResumo;
import br.senac.qs.recursos.application.material.MovimentarMaterial;
import br.senac.qs.recursos.application.material.PendenciaResumo;
import br.senac.qs.recursos.domain.model.EstadoConservacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retirada e devolução de materiais (RF09), pendências e histórico do item. Registradas no balcão
 * por um RESPONSAVEL ou ADMINISTRADOR.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('RESPONSAVEL', 'ADMINISTRADOR')")
public class MovimentacaoController {

    private final MovimentarMaterial movimentar;

    public MovimentacaoController(MovimentarMaterial movimentar) {
        this.movimentar = movimentar;
    }

    public record RetiradaRequest(@Size(max = 500) String observacao) {
    }

    public record DevolucaoRequest(@NotNull EstadoConservacao estadoConservacao, @Size(max = 500) String observacao) {
    }

    @PostMapping("/reservas/{reservaId}/materiais/{materialId}/retirada")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimentacaoResumo retirar(@PathVariable Long reservaId, @PathVariable Long materialId,
                                      @Valid @RequestBody(required = false) RetiradaRequest requisicao,
                                      @AuthenticationPrincipal Jwt jwt) {
        var observacao = requisicao == null ? null : requisicao.observacao();
        return movimentar.retirar(reservaId, materialId, jwt.getSubject(), observacao);
    }

    @PostMapping("/reservas/{reservaId}/materiais/{materialId}/devolucao")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimentacaoResumo devolver(@PathVariable Long reservaId, @PathVariable Long materialId,
                                       @Valid @RequestBody DevolucaoRequest requisicao,
                                       @AuthenticationPrincipal Jwt jwt) {
        return movimentar.devolver(reservaId, materialId, jwt.getSubject(), requisicao.estadoConservacao(),
                requisicao.observacao());
    }

    /** Itens que estão com alguém: quem reservou, desde quando e se já passou do horário. */
    @GetMapping("/movimentacoes/pendentes")
    public List<PendenciaResumo> pendentes() {
        return movimentar.pendentes();
    }

    @GetMapping("/materiais/{id}/movimentacoes")
    public List<MovimentacaoResumo> historico(@PathVariable Long id) {
        return movimentar.historico(id);
    }
}
