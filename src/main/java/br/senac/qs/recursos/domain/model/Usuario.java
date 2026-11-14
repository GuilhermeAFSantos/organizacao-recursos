package br.senac.qs.recursos.domain.model;

import java.time.Duration;
import java.time.Instant;

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
        throw new UnsupportedOperationException("ainda não implementado");
    }

    public boolean estaBloqueado(Instant agora) {
        return false;
    }

    public void registrarFalhaDeLogin(Instant agora) {
        throw new UnsupportedOperationException("ainda não implementado");
    }

    public void registrarLoginComSucesso() {
        throw new UnsupportedOperationException("ainda não implementado");
    }

    public void desativar(Usuario administrador) {
        throw new UnsupportedOperationException("ainda não implementado");
    }

    public void trocarSenha(String novoHash) {
        throw new UnsupportedOperationException("ainda não implementado");
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
