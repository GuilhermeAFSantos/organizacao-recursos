package br.senac.qs.recursos.domain.model;

import java.time.Instant;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Retirada ou devolução de um item de material (RF09). Histórico somente de inserção: uma vez
 * registrada, a movimentação não muda (a V5 também impede UPDATE e DELETE no banco).
 */
@Entity
@Immutable
@Table(name = "movimentacao_material")
public class MovimentacaoMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id")
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id")
    private Reserva reserva;

    @Enumerated(EnumType.STRING)
    private TipoMovimentacao tipo;

    @Column(name = "data_hora")
    private Instant dataHora;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_conservacao")
    private EstadoConservacao estadoConservacao;

    private String observacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrado_por_id")
    private Usuario registradoPor;

    protected MovimentacaoMaterial() {
    }

    /**
     * Entrega do item a quem reservou. {@code ultima} é a movimentação mais recente do material, em
     * qualquer reserva (nula se nunca saiu): se for uma retirada, o item ainda está com alguém.
     */
    public static MovimentacaoMaterial retirar(Reserva reserva, Material material, Usuario autor,
                                               MovimentacaoMaterial ultima, String observacao, Instant agora) {
        exigirResponsavelOuAdministrador(autor);
        exigirDaReserva(reserva, material);
        if (reserva.getStatus() != StatusReserva.APROVADA && reserva.getStatus() != StatusReserva.EM_USO) {
            throw new RegraNegocioException("Só é possível retirar material de reserva aprovada ou em uso.");
        }
        if (ultima != null && ultima.tipo == TipoMovimentacao.RETIRADA) {
            throw new RegraNegocioException(material.identificacao() + (mesmaReserva(ultima.reserva, reserva)
                    ? " já foi retirado e ainda não foi devolvido." : " ainda não foi devolvido de outra reserva."));
        }
        if (material.getEstadoConservacao() == EstadoConservacao.DANIFICADO) {
            throw new RegraNegocioException(material.identificacao() + " está danificado e não pode ser retirado.");
        }
        return nova(TipoMovimentacao.RETIRADA, reserva, material, autor, material.getEstadoConservacao(), observacao,
                agora);
    }

    /**
     * Volta do item, com o estado conferido no balcão. O material passa a ter esse estado: se voltar
     * danificado, deixa de ser oferecido e reservado até alguém registrar o conserto no cadastro.
     */
    @SuppressWarnings("java:S107") // os dados de uma devolução: separar em objeto só esconderia o que se exige
    public static MovimentacaoMaterial devolver(Reserva reserva, Material material, Usuario autor,
                                                MovimentacaoMaterial ultima, EstadoConservacao estado,
                                                String observacao, Instant agora) {
        exigirResponsavelOuAdministrador(autor);
        exigirDaReserva(reserva, material);
        if (ultima == null || ultima.tipo != TipoMovimentacao.RETIRADA || !mesmaReserva(ultima.reserva, reserva)) {
            throw new RegraNegocioException(material.identificacao() + " não está retirado nesta reserva.");
        }
        if (estado == null) {
            throw new RegraNegocioException("Informe o estado de conservação na devolução.");
        }
        if (estado == EstadoConservacao.DANIFICADO && semTexto(observacao)) {
            throw new RegraNegocioException("Descreva o dano na observação.");
        }
        material.registrarConservacao(estado);
        return nova(TipoMovimentacao.DEVOLUCAO, reserva, material, autor, estado, observacao, agora);
    }

    /** Devolução registrada depois do fim da reserva. */
    public boolean isAtrasada() {
        return tipo == TipoMovimentacao.DEVOLUCAO && dataHora.isAfter(reserva.getPeriodo().fim());
    }

    private static MovimentacaoMaterial nova(TipoMovimentacao tipo, Reserva reserva, Material material, Usuario autor,
                                             EstadoConservacao estado, String observacao, Instant agora) {
        var movimentacao = new MovimentacaoMaterial();
        movimentacao.tipo = tipo;
        movimentacao.reserva = reserva;
        movimentacao.material = material;
        movimentacao.registradoPor = autor;
        movimentacao.estadoConservacao = estado;
        movimentacao.observacao = semTexto(observacao) ? null : observacao.strip();
        movimentacao.dataHora = agora;
        return movimentacao;
    }

    private static void exigirResponsavelOuAdministrador(Usuario autor) {
        if (autor.getPerfil() != Perfil.RESPONSAVEL && autor.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new OperacaoNaoPermitidaException(
                    "Somente um RESPONSAVEL ou um ADMINISTRADOR registra retirada e devolução de materiais.");
        }
    }

    private static void exigirDaReserva(Reserva reserva, Material material) {
        if (!reserva.incluiRecurso(material)) {
            throw new RegraNegocioException(material.identificacao() + " não faz parte desta reserva.");
        }
    }

    /** Mesma instância ou mesmo id (a reserva da movimentação pode ser um proxy do Hibernate). */
    private static boolean mesmaReserva(Reserva a, Reserva b) {
        return a == b || (a.getId() != null && a.getId().equals(b.getId()));
    }

    private static boolean semTexto(String texto) {
        return texto == null || texto.isBlank();
    }

    public Long getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public Reserva getReserva() {
        return reserva;
    }

    public TipoMovimentacao getTipo() {
        return tipo;
    }

    public Instant getDataHora() {
        return dataHora;
    }

    public EstadoConservacao getEstadoConservacao() {
        return estadoConservacao;
    }

    public String getObservacao() {
        return observacao;
    }

    public Usuario getRegistradoPor() {
        return registradoPor;
    }
}
