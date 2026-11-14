package br.senac.qs.recursos.application.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import br.senac.qs.recursos.application.UsuarioDoToken;
import br.senac.qs.recursos.domain.exception.ConflitoHorarioException;
import br.senac.qs.recursos.domain.model.Reserva;
import br.senac.qs.recursos.domain.model.ConflitoEvitado;
import br.senac.qs.recursos.domain.model.OrigemConflito;
import br.senac.qs.recursos.domain.model.Perfil;
import br.senac.qs.recursos.domain.model.Professor;
import br.senac.qs.recursos.domain.model.Sala;
import br.senac.qs.recursos.domain.model.TipoSala;
import br.senac.qs.recursos.domain.model.Usuario;
import br.senac.qs.recursos.domain.repository.ConflitoEvitadoRepository;
import br.senac.qs.recursos.domain.repository.MaterialRepository;
import br.senac.qs.recursos.domain.repository.OcupacaoRepository;
import br.senac.qs.recursos.domain.repository.ProfessorRepository;
import br.senac.qs.recursos.domain.repository.RecursoRepository;
import br.senac.qs.recursos.domain.repository.RegistroAuditoriaRepository;
import br.senac.qs.recursos.domain.repository.ReservaRepository;
import br.senac.qs.recursos.domain.repository.SalaRepository;
import br.senac.qs.recursos.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Teste de UNIDADE isolado (mocks permitidos aqui): reproduz de forma determinística o que, no
 * teste de concorrência, só acontece quando duas requisições passam juntas pela pré-checagem —
 * o banco recusa a segunda com SQLSTATE 23P01 (exclusion_violation).
 */
@DisplayName("CriarReserva: tradução da recusa do banco (RC03)")
class CriarReservaTest {

    static final Instant INICIO = Instant.parse("2026-12-01T11:00:00Z");
    static final Instant FIM = Instant.parse("2026-12-01T13:00:00Z");

    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    RecursoRepository recursos = mock(RecursoRepository.class);
    SalaRepository salas = mock(SalaRepository.class);
    OcupacaoRepository ocupacoes = mock(OcupacaoRepository.class);
    ReservaRepository reservas = mock(ReservaRepository.class);

    RegistroAuditoriaRepository auditoria = mock(RegistroAuditoriaRepository.class);

    ProfessorRepository professores = mock(ProfessorRepository.class);
    MaterialRepository materiais = mock(MaterialRepository.class);

    Clock clock = Clock.fixed(Instant.parse("2026-11-30T12:00:00Z"), ZoneOffset.UTC);
    ConflitoEvitadoRepository conflitosEvitados = mock(ConflitoEvitadoRepository.class);

    /** Transações simuladas: o teste é sobre a tradução do erro e o registro do conflito, não sobre o banco. */
    CriarReserva casoDeUso = new CriarReserva(new UsuarioDoToken(usuarios), new AgendaDeRecursos(recursos, ocupacoes),
            salas, professores, materiais, reservas, new HistoricoDaReserva(auditoria, evento -> { }),
            new RegistrarConflitoEvitado(mock(PlatformTransactionManager.class), conflitosEvitados, clock), clock);

    CriarReservaComando comando = new CriarReservaComando("ana@senac.br", 7L, null, List.of(), INICIO, FIM, "Aula");

    final Usuario adm = Usuario.cadastrar("Adm", "adm@senac.br", "hash", Perfil.ADMINISTRADOR, INICIO);

    @BeforeEach
    void cenarioEmQueAPreChecagemNaoVeConflito() {
        var usuario = mock(Usuario.class);
        when(usuario.isAtivo()).thenReturn(true);
        var sala = Sala.cadastrar(adm, "Sala 101", false, 40, "A", 1, TipoSala.AULA);

        when(usuarios.findByEmailIgnoreCase("ana@senac.br")).thenReturn(Optional.of(usuario));
        when(recursos.travar(7L)).thenReturn(Optional.of(7L));
        when(salas.findById(7L)).thenReturn(Optional.of(sala));
        // A outra transação ainda não confirmou: a consulta prévia vê o horário livre (lista vazia,
        // o padrão do mock).
    }

    @Test
    @DisplayName("recusa do banco por sobreposição (23P01) vira ConflitoHorarioException com mensagem amigável")
    void traduzViolacaoDeExclusao() {
        bancoRecusaGravacaoCom("23P01");

        assertThatThrownBy(() -> casoDeUso.executar(comando))
                .isInstanceOf(ConflitoHorarioException.class)
                .hasMessage("A sala Sala 101 já está ocupada nesse horário. Escolha outro horário ou outra sala.");
        assertThat(conflitoRegistrado()).as("RF12: a recusa do banco também é conflito evitado, com origem BANCO")
                .satisfies(c -> {
                    assertThat(c.getOrigem()).isEqualTo(OrigemConflito.BANCO);
                    assertThat(c.getPeriodo().inicio()).isEqualTo(INICIO);
                });
    }

    @Test
    @DisplayName("com vários recursos o banco não diz qual conflitou: mensagem genérica, sem inventar")
    void variosRecursos() {
        when(professores.findById(8L)).thenReturn(Optional.of(
                Professor.cadastrar(adm, "Carlos Lima", "P-1", null, Set.of())));
        bancoRecusaGravacaoCom("23P01");

        assertThatThrownBy(() -> casoDeUso.executar(
                new CriarReservaComando("ana@senac.br", 7L, 8L, List.of(), INICIO, FIM, "Aula")))
                .isInstanceOf(ConflitoHorarioException.class)
                .hasMessage("Um dos recursos já está ocupado nesse horário. Escolha outro horário.");
        assertThat(conflitoRegistrado().getRecursoId()).as("o banco não diz qual recurso conflitou").isNull();
    }

    @Test
    @DisplayName("outras violações de integridade NÃO são mascaradas como conflito de horário")
    void naoMascaraOutrasViolacoes() {
        bancoRecusaGravacaoCom("23503"); // foreign_key_violation

        assertThatThrownBy(() -> casoDeUso.executar(comando))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(conflitosEvitados, never()).save(any());
    }

    ConflitoEvitado conflitoRegistrado() {
        var captor = ArgumentCaptor.forClass(ConflitoEvitado.class);
        verify(conflitosEvitados).save(captor.capture());
        return captor.getValue();
    }

    /**
     * Simula a recusa na gravação, seja por {@code save} ou {@code saveAndFlush}: o teste verifica
     * a TRADUÇÃO do erro, não qual método de gravação o caso de uso escolheu.
     */
    private void bancoRecusaGravacaoCom(String sqlState) {
        var erro = new DataIntegrityViolationException("could not execute statement",
                new SQLException("violação simulada", sqlState));
        lenient().when(reservas.save(any(Reserva.class))).thenThrow(erro);
        lenient().when(reservas.saveAndFlush(any(Reserva.class))).thenThrow(erro);
    }
}
