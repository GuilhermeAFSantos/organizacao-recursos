package br.senac.qs.recursos.domain.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "sala")
@DiscriminatorValue("SALA")
@PrimaryKeyJoinColumn(name = "id")
public class Sala extends Recurso {

    protected Sala() {
    }
}
