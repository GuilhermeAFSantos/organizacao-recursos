package br.senac.qs.recursos.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Registro de auditoria (RF10, RC08): quem, quando, estado anterior, estado novo e motivo.
 *
 * <p>Somente inserção. {@code @Immutable} impede o Hibernate de gerar UPDATE, e o trigger da
 * migration V1 bloqueia UPDATE, DELETE e TRUNCATE mesmo por SQL direto.
 */
@Entity
@Immutable
@Table(name = "registro_auditoria")
public class RegistroAuditoria {

    static final String ENTIDADE_RESERVA = "RESERVA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String entidade;

    @Column(name = "entidade_id")
    private Long entidadeId;

    private String acao;

    @Column(name = "estado_anterior")
    private String estadoAnterior;

    @Column(name = "estado_novo")
    private String estadoNovo;

    private String motivo;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "usuario_email")
    private String usuarioEmail;

    @Column(name = "ocorrido_em")
    private Instant ocorridoEm;

    protected RegistroAuditoria() {
    }

    public static RegistroAuditoria daReserva(Long reservaId, MudancaDeEstado mudanca) {
        var registro = new RegistroAuditoria();
        registro.entidade = ENTIDADE_RESERVA;
        registro.entidadeId = reservaId;
        registro.acao = mudanca.anterior() == null ? "CRIACAO" : "TRANSICAO";
        registro.estadoAnterior = mudanca.anterior() == null ? null : mudanca.anterior().name();
        registro.estadoNovo = mudanca.novo().name();
        registro.motivo = mudanca.motivo();
        registro.usuarioId = mudanca.autorId();
        registro.usuarioEmail = mudanca.autorEmail();
        registro.ocorridoEm = mudanca.quando();
        return registro;
    }
}
