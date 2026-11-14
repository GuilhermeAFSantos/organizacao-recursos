package br.senac.qs.recursos.domain.model;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "recurso")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo")
public abstract class Recurso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    /** RF07/RC06: reservas deste recurso exigem aprovação de um RESPONSAVEL. */
    private boolean restrito;

    private boolean ativo;

    /** Lock otimista: duas alterações simultâneas do mesmo cadastro não se sobrescrevem em silêncio. */
    @Version
    private long version;

    protected Recurso() {
    }

    protected Recurso(String nome, boolean restrito) {
        this.nome = nome;
        this.restrito = restrito;
        this.ativo = true;
    }

    public abstract TipoRecurso getTipo();

    public void desativar(Usuario autor) {
        exigirAdministrador(autor);
        if (!ativo) {
            throw new RegraNegocioException("O recurso já está inativo.");
        }
        this.ativo = false;
    }

    public void reativar(Usuario autor) {
        exigirAdministrador(autor);
        if (ativo) {
            throw new RegraNegocioException("O recurso já está ativo.");
        }
        this.ativo = true;
    }

    /** Dados comuns a todo recurso; chamado pelo cadastro e pela alteração de cada tipo. */
    protected void definirDadosGerais(String nome, boolean restrito) {
        this.nome = obrigatorio(nome, "Informe o nome do recurso.");
        this.restrito = restrito;
    }

    protected static void exigirAdministrador(Usuario autor) {
        if (autor.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new OperacaoNaoPermitidaException("Somente um ADMINISTRADOR pode cadastrar ou alterar recursos.");
        }
    }

    /** Texto obrigatório, sem espaços nas pontas. */
    protected static String obrigatorio(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new RegraNegocioException(mensagem);
        }
        return valor.strip();
    }

    protected static <T> T informado(T valor, String mensagem) {
        if (valor == null) {
            throw new RegraNegocioException(mensagem);
        }
        return valor;
    }

    /** Nome usado nas mensagens ao usuário. */
    public String getNomeParaExibicao() {
        return nome;
    }

    /** Ex.: "A sala Sala 101". */
    public String identificacao() {
        return getTipo().identificar(getNomeParaExibicao());
    }

    /** Só recurso ativo aceita reservas. */
    public void exigirReservavel() {
        if (!ativo) {
            throw new RegraNegocioException(identificacao() + " está " + getTipo().concordar("desativado")
                    + " e não aceita reservas.");
        }
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public boolean isRestrito() {
        return restrito;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
