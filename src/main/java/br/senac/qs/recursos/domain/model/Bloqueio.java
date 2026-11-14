package br.senac.qs.recursos.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** Período em que um recurso não pode ser reservado: manutenção ou bloqueio administrativo (RF08, RC04). */
@Entity
@Table(name = "bloqueio")
public class Bloqueio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TipoBloqueio tipo;

    private String motivo;

    @Embedded
    private PeriodoReserva periodo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_id")
    private Usuario criadoPor;

    @Column(name = "criado_em")
    private Instant criadoEm;

    @OneToMany(mappedBy = "bloqueio", cascade = CascadeType.PERSIST)
    private List<Ocupacao> ocupacoes = new ArrayList<>();

    protected Bloqueio() {
    }

    public static Bloqueio registrar(Usuario administrador, Recurso recurso, TipoBloqueio tipo,
                                     PeriodoReserva periodo, String motivo, Instant agora) {
        exigirAdministrador(administrador);
        if (motivo == null || motivo.isBlank()) {
            throw new RegraNegocioException("Informe o motivo do bloqueio.");
        }
        var bloqueio = new Bloqueio();
        bloqueio.tipo = tipo;
        bloqueio.motivo = motivo;
        bloqueio.periodo = periodo;
        bloqueio.criadoPor = administrador;
        bloqueio.criadoEm = agora;
        bloqueio.ocupacoes.add(new Ocupacao(bloqueio, recurso, periodo));
        return bloqueio;
    }

    /**
     * Q10: encerra antes do fim previsto. Durante o bloqueio, o período passa a terminar no próximo
     * minuto cheio (Q3); antes de começar, o bloqueio inteiro é desfeito.
     */
    public void encerrar(Usuario administrador, Instant agora) {
        if (!isAtivo()) {
            throw new RegraNegocioException("Este bloqueio já foi encerrado.");
        }
        if (!agora.isBefore(periodo.fim())) {
            throw new RegraNegocioException("Este bloqueio já terminou.");
        }
        var novoFim = proximoMinutoCheio(agora);
        if (novoFim.isAfter(periodo.inicio())) {
            encurtarAte(novoFim);
        } else {
            desfazer();
        }
    }

    /** Durante o bloqueio: passa a terminar em {@code novoFim}, e o recurso fica livre depois disso. */
    private void encurtarAte(Instant novoFim) {
        periodo = new PeriodoReserva(periodo.inicio(), novoFim);
        ocupacoes.forEach(ocupacao -> ocupacao.encurtarAte(novoFim));
    }

    /** Antes de começar: o bloqueio inteiro deixa de ocupar o recurso. */
    private void desfazer() {
        ocupacoes.forEach(Ocupacao::liberar);
    }

    /** 10:37:42 → 10:38:00; 10:37:00 → 10:37:00. Mantém os períodos em minutos cheios (Q3). */
    private static Instant proximoMinutoCheio(Instant instante) {
        var truncado = instante.truncatedTo(ChronoUnit.MINUTES);
        return truncado.equals(instante) ? truncado : truncado.plus(1, ChronoUnit.MINUTES);
    }

    private static void exigirAdministrador(Usuario usuario) {
        if (usuario.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new OperacaoNaoPermitidaException("Somente um ADMINISTRADOR pode registrar ou encerrar bloqueios.");
        }
    }

    public boolean isAtivo() {
        return ocupacoes.stream().anyMatch(Ocupacao::isBloqueante);
    }

    public Long getId() {
        return id;
    }

    public TipoBloqueio getTipo() {
        return tipo;
    }

    public String getMotivo() {
        return motivo;
    }

    public PeriodoReserva getPeriodo() {
        return periodo;
    }

    public List<Ocupacao> getOcupacoes() {
        return List.copyOf(ocupacoes);
    }
}
