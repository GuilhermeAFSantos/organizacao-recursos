package br.senac.qs.recursos.web.bloqueio;

import br.senac.qs.recursos.application.bloqueio.GerenciarBloqueios;
import br.senac.qs.recursos.application.bloqueio.RegistrarBloqueioComando;
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

/** Manutenção e bloqueios administrativos de recursos (RF08). Exclusivo do ADMINISTRADOR. */
@RestController
@RequestMapping("/api/bloqueios")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class BloqueioController {

    private final GerenciarBloqueios bloqueios;

    public BloqueioController(GerenciarBloqueios bloqueios) {
        this.bloqueios = bloqueios;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueioResponse registrar(@Valid @RequestBody RegistrarBloqueioRequest requisicao,
                                      @AuthenticationPrincipal Jwt jwt) {
        var comando = new RegistrarBloqueioComando(jwt.getSubject(), requisicao.recursoId(), requisicao.tipo(),
                requisicao.inicio().toInstant(), requisicao.fim().toInstant(), requisicao.motivo());
        return BloqueioResponse.de(bloqueios.registrar(comando));
    }

    @PostMapping("/{id}/encerramento")
    public BloqueioResponse encerrar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return BloqueioResponse.de(bloqueios.encerrar(id, jwt.getSubject()));
    }
}
