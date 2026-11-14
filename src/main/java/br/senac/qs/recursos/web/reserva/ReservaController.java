package br.senac.qs.recursos.web.reserva;

import br.senac.qs.recursos.application.reserva.CriarReservaSala;
import br.senac.qs.recursos.application.reserva.CriarReservaSalaComando;
import br.senac.qs.recursos.application.reserva.TransicionarReserva;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reservas (RF04) e suas mudanças de estado (RC05). Cada mudança é um sub-recurso de ação
 * ({@code POST /api/reservas/{id}/aprovacao}), o que deixa a autorização por perfil explícita em
 * cada endpoint. As regras de quem pode agir sobre QUAL reserva ficam no domínio.
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private static final String RESPONSAVEL = "hasRole('RESPONSAVEL')";

    private final CriarReservaSala criarReservaSala;
    private final TransicionarReserva transicionar;

    public ReservaController(CriarReservaSala criarReservaSala, TransicionarReserva transicionar) {
        this.criarReservaSala = criarReservaSala;
        this.transicionar = transicionar;
    }

    @PostMapping
    @PreAuthorize("hasRole('SOLICITANTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponse criar(@Valid @RequestBody CriarReservaRequest requisicao,
                                 @AuthenticationPrincipal Jwt jwt) {
        var comando = new CriarReservaSalaComando(jwt.getSubject(), requisicao.salaId(),
                requisicao.inicio().toInstant(), requisicao.fim().toInstant(), requisicao.finalidade());
        return ReservaResponse.de(criarReservaSala.executar(comando));
    }

    @PostMapping("/{id}/aprovacao")
    @PreAuthorize(RESPONSAVEL)
    public ReservaResponse aprovar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ReservaResponse.de(transicionar.aprovar(id, jwt.getSubject()));
    }

    @PostMapping("/{id}/rejeicao")
    @PreAuthorize(RESPONSAVEL)
    public ReservaResponse rejeitar(@PathVariable Long id, @Valid @RequestBody MotivoRequest requisicao,
                                    @AuthenticationPrincipal Jwt jwt) {
        return ReservaResponse.de(transicionar.rejeitar(id, jwt.getSubject(), requisicao.motivo()));
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("hasAnyRole('SOLICITANTE', 'ADMINISTRADOR')")
    public ReservaResponse cancelar(@PathVariable Long id,
                                    @Valid @RequestBody(required = false) MotivoOpcionalRequest requisicao,
                                    @AuthenticationPrincipal Jwt jwt) {
        return ReservaResponse.de(transicionar.cancelar(id, jwt.getSubject(), MotivoOpcionalRequest.de(requisicao)));
    }

    @PostMapping("/{id}/inicio-uso")
    @PreAuthorize(RESPONSAVEL)
    public ReservaResponse iniciarUso(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ReservaResponse.de(transicionar.iniciarUso(id, jwt.getSubject()));
    }

    @PostMapping("/{id}/nao-comparecimento")
    @PreAuthorize(RESPONSAVEL)
    public ReservaResponse registrarNaoComparecimento(@PathVariable Long id,
            @Valid @RequestBody(required = false) MotivoOpcionalRequest requisicao,
            @AuthenticationPrincipal Jwt jwt) {
        return ReservaResponse.de(transicionar.registrarNaoComparecimento(id, jwt.getSubject(),
                MotivoOpcionalRequest.de(requisicao)));
    }

    @PostMapping("/{id}/conclusao")
    @PreAuthorize(RESPONSAVEL)
    public ReservaResponse concluir(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ReservaResponse.de(transicionar.concluir(id, jwt.getSubject()));
    }
}
