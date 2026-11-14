package br.senac.qs.recursos.application.reserva;

import java.time.Clock;

import br.senac.qs.recursos.domain.exception.ConflitoHorarioException;
import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.model.PeriodoReserva;
import br.senac.qs.recursos.domain.model.Reserva;
import br.senac.qs.recursos.domain.repository.OcupacaoRepository;
import br.senac.qs.recursos.domain.repository.ReservaRepository;
import br.senac.qs.recursos.domain.repository.SalaRepository;
import br.senac.qs.recursos.domain.repository.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CriarReservaSala {

    private final UsuarioRepository usuarios;
    private final SalaRepository salas;
    private final OcupacaoRepository ocupacoes;
    private final ReservaRepository reservas;
    private final Clock clock;

    public CriarReservaSala(UsuarioRepository usuarios, SalaRepository salas, OcupacaoRepository ocupacoes,
                            ReservaRepository reservas, Clock clock) {
        this.usuarios = usuarios;
        this.salas = salas;
        this.ocupacoes = ocupacoes;
        this.reservas = reservas;
        this.clock = clock;
    }

    @Transactional
    public ReservaCriada executar(CriarReservaSalaComando comando) {
        var periodo = new PeriodoReserva(comando.inicio(), comando.fim());
        var solicitante = usuarios.findByEmailIgnoreCase(comando.emailSolicitante())
                .filter(u -> u.isAtivo())
                .orElseThrow(() -> new AccessDeniedException("usuário do token não está ativo"));
        var sala = salas.findById(comando.salaId())
                .filter(s -> s.isAtivo())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Sala " + comando.salaId() + " não encontrada."));
        if (ocupacoes.existeSobreposicao(sala.getId(), periodo.inicio(), periodo.fim())) {
            throw new ConflitoHorarioException("A sala " + sala.getNome()
                    + " já está ocupada nesse horário. Escolha outro horário ou outra sala.");
        }
        var reserva = reservas.save(Reserva.solicitar(solicitante, sala, periodo, comando.finalidade(),
                clock.instant()));
        return new ReservaCriada(reserva.getId(), sala.getId(), periodo.inicio(), periodo.fim(),
                reserva.getFinalidade(), reserva.getStatus());
    }
}
