package br.senac.qs.recursos.domain.model;

import jakarta.persistence.Entity;
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

    private boolean ativo;

    protected Usuario() {
    }

    public Long getId() {
        return id;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
