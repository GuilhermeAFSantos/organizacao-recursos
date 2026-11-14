package br.senac.qs.recursos.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario {

    /** Falhas de login seguidas que bloqueiam o usuário (proteção contra força bruta). */
    public static final int MAXIMO_TENTATIVAS = 5;
    public static final Duration DURACAO_BLOQUEIO = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    private String email;

    @Column(name = "senha_hash")
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    private Perfil perfil;

    private boolean ativo;

    @Column(name = "criado_em")
    private Instant criadoEm;

    @Column(name = "tentativas_falhas")
    private int tentativasFalhas;

    @Column(name = "bloqueado_ate")
    private Instant bloqueadoAte;

    protected Usuario() {
    }

    /** Usado pelos testes de unidade do domínio. */
    Usuario(String email, Perfil perfil) {
        this.email = email;
        this.perfil = perfil;
        this.ativo = true;
    }

    public static Usuario cadastrar(String nome, String email, String senhaHash, Perfil perfil, Instant agora) {
        var usuario = new Usuario();
        usuario.nome = nome;
        usuario.email = normalizarEmail(email);
        usuario.senhaHash = senhaHash;
        usuario.perfil = perfil;
        usuario.ativo = true;
        usuario.criadoEm = agora;
        return usuario;
    }

    /** E-mail em minúsculas e sem espaços nas pontas: a mesma pessoa não vira dois cadastros. */
    public static String normalizarEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public boolean estaBloqueado(Instant agora) {
        return bloqueadoAte != null && agora.isBefore(bloqueadoAte);
    }

    /** A 5ª falha seguida bloqueia por 15 minutos; passado o bloqueio, a contagem recomeça. */
    public void registrarFalhaDeLogin(Instant agora) {
        if (bloqueadoAte != null && !estaBloqueado(agora)) {
            bloqueadoAte = null;
        }
        tentativasFalhas++;
        if (tentativasFalhas >= MAXIMO_TENTATIVAS) {
            bloqueadoAte = agora.plus(DURACAO_BLOQUEIO);
            tentativasFalhas = 0;
        }
    }

    public void registrarLoginComSucesso() {
        tentativasFalhas = 0;
        bloqueadoAte = null;
    }

    public void desativar(Usuario administrador) {
        if (administrador.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new OperacaoNaoPermitidaException("Somente um ADMINISTRADOR pode gerenciar usuários.");
        }
        if (mesmaPessoaQue(administrador)) {
            throw new RegraNegocioException("Você não pode desativar o próprio usuário.");
        }
        ativo = false;
    }

    public void trocarSenha(String novoHash) {
        this.senhaHash = novoHash;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getBloqueadoAte() {
        return bloqueadoAte;
    }

    /** Mesma pessoa? Compara pelo e-mail, que é único no sistema (sem diferenciar maiúsculas). */
    public boolean mesmaPessoaQue(Usuario outro) {
        return email.equalsIgnoreCase(outro.getEmail());
    }
}
