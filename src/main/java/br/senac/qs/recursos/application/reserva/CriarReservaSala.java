package br.senac.qs.recursos.application.reserva;

import java.sql.SQLException;
import java.time.Clock;

import br.senac.qs.recursos.domain.exception.ConflitoHorarioException;
import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.model.PeriodoReserva;
import br.senac.qs.recursos.domain.model.Recurso;
import br.senac.qs.recursos.domain.model.Reserva;
import br.senac.qs.recursos.domain.model.Sala;
import br.senac.qs.recursos.domain.model.Usuario;
import br.senac.qs.recursos.domain.repository.OcupacaoRepository;
import br.senac.qs.recursos.domain.repository.ReservaRepository;
import br.senac.qs.recursos.domain.repository.SalaRepository;
import br.senac.qs.recursos.domain.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: um SOLICITANTE reserva uma sala (RF04).
 *
 * <p>Ordem das verificações: RC01 (período válido) → solicitante ativo → sala existente e ativa →
 * RC02 (horário livre) → grava. A reserva nasce SOLICITADA e já ocupa o horário (decisão D2).
 */
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
        var solicitante = solicitanteAtivo(comando.emailSolicitante());
        var sala = salaAtiva(comando.salaId());
        garantirHorarioLivre(sala, periodo);

        Reserva reserva;
        try {
            reserva = reservas.saveAndFlush(
                    Reserva.solicitar(solicitante, sala, periodo, comando.finalidade(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            if (sqlState(e).equals("23P01")) {
                throw conflito(sala);
            }
            throw e;
        }
        return new ReservaCriada(reserva.getId(), sala.getId(), periodo.inicio(), periodo.fim(),
                reserva.getFinalidade(), reserva.getStatus());
    }

    /** O token é válido, mas o usuário pode ter sido desativado depois da emissão. */
    private Usuario solicitanteAtivo(String email) {
        return usuarios.findByEmailIgnoreCase(email)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new AccessDeniedException("usuário do token inexistente ou inativo"));
    }

    private Sala salaAtiva(Long salaId) {
        return salas.findById(salaId)
                .filter(Recurso::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala " + salaId + " não encontrada."));
    }

    /**
     * RC02, primeira barreira: a consulta prévia dá uma mensagem clara ao usuário no caso comum.
     * Sozinha ela NÃO basta sob concorrência (duas transações podem consultar ao mesmo tempo e ver o
     * horário livre). A garantia definitiva é a constraint de exclusão do banco (RC03).
     */
    private void garantirHorarioLivre(Sala sala, PeriodoReserva periodo) {
        if (ocupacoes.existeSobreposicao(sala.getId(), periodo.inicio(), periodo.fim())) {
            throw conflito(sala);
        }
    }

    private static ConflitoHorarioException conflito(Sala sala) {
        return new ConflitoHorarioException("A sala " + sala.getNome()
                + " já está ocupada nesse horário. Escolha outro horário ou outra sala.");
    }

    private static String sqlState(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return "";
    }
}
