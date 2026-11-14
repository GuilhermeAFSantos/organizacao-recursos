package br.senac.qs.recursos.web.reserva;

import br.senac.qs.recursos.application.Pagina;
import br.senac.qs.recursos.application.reserva.AlterarHorarioReserva;
import br.senac.qs.recursos.application.reserva.ConsultarReservas;
import br.senac.qs.recursos.application.reserva.CriarReserva;
import br.senac.qs.recursos.application.reserva.CriarReservaComando;
import br.senac.qs.recursos.application.reserva.TransicionarReserva;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    private final CriarReserva criarReserva;
    private final TransicionarReserva transicionar;
    private final AlterarHorarioReserva alterarHorario;
    private final ConsultarReservas consultar;

    public ReservaController(CriarReserva criarReserva, TransicionarReserva transicionar,
                             AlterarHorarioReserva alterarHorario, ConsultarReservas consultar) {
        this.criarReserva = criarReserva;
        this.transicionar = transicionar;
        this.alterarHorario = alterarHorario;
        this.consultar = consultar;
    }

    /**
     * Reservas em ordem de início. O SOLICITANTE recebe só as próprias; RESPONSAVEL e ADMINISTRADOR,
     * todas (ex.: {@code ?status=SOLICITADA} é a fila de aprovação).
     */
    @GetMapping
    public Pagina<ReservaResponse> listar(@Valid @ModelAttribute FiltroReservasRequest filtros,
                                          @AuthenticationPrincipal Jwt jwt) {
        return consultar.listar(jwt.getSubject(), filtros.status(), filtros.de(), filtros.ate(), filtros.pagina(),
                filtros.tamanho()).converter(ReservaResponse::de);
    }

    @PostMapping
    @PreAuthorize("hasRole('SOLICITANTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponse criar(@Valid @RequestBody CriarReservaRequest requisicao,
                                 @AuthenticationPrincipal Jwt jwt) {
        var comando = new CriarReservaComando(jwt.getSubject(), requisicao.salaId(), requisicao.professorId(),
                requisicao.materiaisIds(), requisicao.inicio().toInstant(), requisicao.fim().toInstant(),
                requisicao.finalidade());
        return ReservaResponse.de(criarReserva.executar(comando));
    }

    /** RF04: a dona da reserva ou um ADMINISTRADOR muda o horário, mantendo os recursos. */
    @PutMapping("/{id}/horario")
    @PreAuthorize("hasAnyRole('SOLICITANTE', 'ADMINISTRADOR')")
    public ReservaResponse alterarHorario(@PathVariable Long id,
                                          @Valid @RequestBody AlterarHorarioRequest requisicao,
                                          @AuthenticationPrincipal Jwt jwt) {
        return ReservaResponse.de(alterarHorario.executar(id, jwt.getSubject(), requisicao.inicio().toInstant(),
                requisicao.fim().toInstant()));
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
