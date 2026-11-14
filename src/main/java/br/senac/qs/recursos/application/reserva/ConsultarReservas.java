package br.senac.qs.recursos.application.reserva;

import static br.senac.qs.recursos.domain.model.PeriodoReserva.BRASILIA;

import java.time.LocalDate;

import br.senac.qs.recursos.application.Pagina;
import br.senac.qs.recursos.application.UsuarioDoToken;
import br.senac.qs.recursos.domain.model.Perfil;
import br.senac.qs.recursos.domain.model.StatusReserva;
import br.senac.qs.recursos.domain.pesquisa.FiltroReservas;
import br.senac.qs.recursos.domain.repository.PesquisaDeReservas;
import br.senac.qs.recursos.domain.repository.ReservaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listagem de reservas (apoio à interface, RF13). O SOLICITANTE só enxerga as próprias, qualquer que
 * seja o filtro pedido; RESPONSAVEL e ADMINISTRADOR enxergam todas (fila de aprovação, agenda do dia).
 */
@Service
public class ConsultarReservas {

    private static final Sort ORDEM = Sort.by("periodo.inicio", "id");

    private final UsuarioDoToken usuarioDoToken;
    private final ReservaRepository reservas;

    public ConsultarReservas(UsuarioDoToken usuarioDoToken, ReservaRepository reservas) {
        this.usuarioDoToken = usuarioDoToken;
        this.reservas = reservas;
    }

    /** {@code de} e {@code ate} são dias de Brasília, ambos incluídos; nulos não filtram. */
    @Transactional(readOnly = true)
    public Pagina<ReservaResumo> listar(String emailUsuario, StatusReserva status, LocalDate de, LocalDate ate,
                                        int pagina, int tamanho) {
        var usuario = usuarioDoToken.ativo(emailUsuario);
        var solicitante = usuario.getPerfil() == Perfil.SOLICITANTE ? usuario.getId() : null;
        var filtro = new FiltroReservas(solicitante, status,
                de == null ? null : de.atStartOfDay(BRASILIA).toInstant(),
                ate == null ? null : ate.plusDays(1).atStartOfDay(BRASILIA).toInstant());
        var resultado = reservas.findAll(PesquisaDeReservas.para(filtro), PageRequest.of(pagina, tamanho, ORDEM));
        return Pagina.de(resultado, ReservaResumo::de);
    }
}
