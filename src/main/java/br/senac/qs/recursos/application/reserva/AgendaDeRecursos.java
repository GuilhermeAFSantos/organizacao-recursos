package br.senac.qs.recursos.application.reserva;

import static br.senac.qs.recursos.domain.model.PeriodoReserva.BRASILIA;

import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import br.senac.qs.recursos.domain.exception.ConflitoHorarioException;
import br.senac.qs.recursos.domain.model.Bloqueio;
import br.senac.qs.recursos.domain.model.Ocupacao;
import br.senac.qs.recursos.domain.model.PeriodoReserva;
import br.senac.qs.recursos.domain.model.Recurso;
import br.senac.qs.recursos.domain.model.TipoBloqueio;
import br.senac.qs.recursos.domain.repository.OcupacaoRepository;
import br.senac.qs.recursos.domain.repository.RecursoRepository;
import org.springframework.stereotype.Component;

/**
 * Agenda dos recursos: o que criação e alteração de reserva compartilham para não haver dupla
 * alocação (RC02, RC03, RC04, RF05).
 *
 * <ol>
 *   <li><b>Travar em ordem crescente de id.</b> Duas reservas que pedem os mesmos recursos em ordens
 *       diferentes ([A, B] e [B, A]) travariam um cada e esperariam pelo outro para sempre: deadlock.
 *       Com uma ordem global única, a segunda sempre espera a primeira terminar.</li>
 *   <li><b>Pré-checagem</b> com mensagem clara (qual recurso, se é manutenção ou outra reserva).
 *       Confiável porque os recursos já estão travados.</li>
 *   <li>A constraint de exclusão do banco continua sendo a garantia final ({@link #conflitoNoBanco}).</li>
 * </ol>
 */
@Component
public class AgendaDeRecursos {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RecursoRepository recursos;
    private final OcupacaoRepository ocupacoes;

    public AgendaDeRecursos(RecursoRepository recursos, OcupacaoRepository ocupacoes) {
        this.recursos = recursos;
        this.ocupacoes = ocupacoes;
    }

    /** Trava até o fim da transação; recursos inativos não são travados (e serão recusados depois). */
    public void travarEmOrdem(Collection<Long> ids) {
        ids.stream().distinct().sorted().forEach(recursos::travar);
    }

    /**
     * Recusa (409) se algum recurso tiver ocupação bloqueante sobreposta ao período, ignorando as da
     * própria reserva que está sendo alterada ({@code reservaIgnorada}; nulo na criação).
     */
    public void garantirLivre(List<Recurso> recursosDaReserva, PeriodoReserva periodo, Long reservaIgnorada) {
        for (var recurso : recursosDaReserva) {
            var conflitos = ocupacoes.sobrepostas(recurso.getId(), periodo.inicio(), periodo.fim()).stream()
                    .filter(o -> o.getReserva() == null || !o.getReserva().getId().equals(reservaIgnorada))
                    .toList();
            if (!conflitos.isEmpty()) {
                throw conflitoCom(recurso, conflitos);
            }
        }
    }

    /**
     * Tradução da recusa da constraint (23P01), que não diz qual recurso conflitou: com um só recurso
     * dá para nomeá-lo; com vários, a mensagem é genérica.
     */
    public ConflitoHorarioException conflitoNoBanco(List<Recurso> recursosDaReserva) {
        if (recursosDaReserva.size() == 1) {
            return ocupado(recursosDaReserva.getFirst());
        }
        return new ConflitoHorarioException("Um dos recursos já está ocupado nesse horário. Escolha outro horário.");
    }

    private static ConflitoHorarioException conflitoCom(Recurso recurso, List<Ocupacao> conflitos) {
        return conflitos.stream().map(Ocupacao::getBloqueio).filter(Objects::nonNull).findFirst()
                .map(bloqueio -> bloqueado(recurso, bloqueio))
                .orElseGet(() -> ocupado(recurso));
    }

    private static ConflitoHorarioException bloqueado(Recurso recurso, Bloqueio bloqueio) {
        var tipo = recurso.getTipo();
        var p = bloqueio.getPeriodo();
        var situacao = bloqueio.getTipo() == TipoBloqueio.MANUTENCAO ? "está em manutenção"
                : "está " + tipo.concordar("bloqueado");
        return new ConflitoHorarioException(recurso.identificacao() + " " + situacao + " das "
                + HORA.format(p.inicio().atZone(BRASILIA)) + " às " + HORA.format(p.fim().atZone(BRASILIA))
                + " de " + DATA.format(p.inicio().atZone(BRASILIA)) + ". Escolha outro horário ou " + tipo.outro() + ".");
    }

    private static ConflitoHorarioException ocupado(Recurso recurso) {
        var tipo = recurso.getTipo();
        return new ConflitoHorarioException(recurso.identificacao() + " já está " + tipo.concordar("ocupado")
                + " nesse horário. Escolha outro horário ou " + tipo.outro() + ".");
    }
}
