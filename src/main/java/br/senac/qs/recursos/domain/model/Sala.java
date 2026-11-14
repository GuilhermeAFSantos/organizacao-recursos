package br.senac.qs.recursos.domain.model;

import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "sala")
@DiscriminatorValue("SALA")
@PrimaryKeyJoinColumn(name = "id")
public class Sala extends Recurso {

    private int capacidade;

    private String bloco;

    private int andar;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_sala")
    private TipoSala tipoSala;

    protected Sala() {
    }

    /** Atalho dos testes de unidade de reserva e bloqueio, que só precisam de nome e restrição. */
    Sala(String nome, boolean restrito) {
        super(nome, restrito);
    }

    public static Sala cadastrar(Usuario autor, String nome, boolean restrito, int capacidade, String bloco,
                                 int andar, TipoSala tipoSala) {
        exigirAdministrador(autor);
        var sala = new Sala(nome, restrito);
        sala.preencher(nome, restrito, capacidade, bloco, andar, tipoSala);
        return sala;
    }

    public void alterar(Usuario autor, String nome, boolean restrito, int capacidade, String bloco, int andar,
                        TipoSala tipoSala) {
        exigirAdministrador(autor);
        preencher(nome, restrito, capacidade, bloco, andar, tipoSala);
    }

    private void preencher(String nome, boolean restrito, int capacidade, String bloco, int andar, TipoSala tipoSala) {
        if (capacidade <= 0) {
            throw new RegraNegocioException("A capacidade da sala deve ser maior que zero.");
        }
        definirDadosGerais(nome, restrito);
        this.capacidade = capacidade;
        this.bloco = obrigatorio(bloco, "Informe o bloco da sala.");
        this.andar = andar;
        this.tipoSala = informado(tipoSala, "Informe o tipo da sala.");
    }

    @Override
    public TipoRecurso getTipo() {
        return TipoRecurso.SALA;
    }

    public int getCapacidade() {
        return capacidade;
    }

    public String getBloco() {
        return bloco;
    }

    public int getAndar() {
        return andar;
    }

    public TipoSala getTipoSala() {
        return tipoSala;
    }
}
