package br.senac.qs.recursos.domain.model;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Enumerated(EnumType.STRING)
    private Perfil perfil;

    private boolean ativo;

    protected Usuario() {
    }

    /** Usado pelos testes de unidade do domínio (o cadastro de usuários chega com o RF01). */
    Usuario(String email, Perfil perfil) {
        this.email = email;
        this.perfil = perfil;
        this.ativo = true;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public boolean isAtivo() {
        return ativo;
    }

    /** Mesma pessoa? Compara pelo e-mail, que é único no sistema (sem diferenciar maiúsculas). */
    public boolean mesmaPessoaQue(Usuario outro) {
        return email.equalsIgnoreCase(outro.getEmail());
    }
}
