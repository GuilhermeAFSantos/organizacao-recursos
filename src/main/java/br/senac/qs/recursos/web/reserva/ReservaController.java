package br.senac.qs.recursos.web.reserva;

import br.senac.qs.recursos.application.reserva.CriarReservaSala;
import br.senac.qs.recursos.application.reserva.CriarReservaSalaComando;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final CriarReservaSala criarReservaSala;

    public ReservaController(CriarReservaSala criarReservaSala) {
        this.criarReservaSala = criarReservaSala;
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
}
