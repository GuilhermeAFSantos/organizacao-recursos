package br.senac.qs.recursos.web.reserva;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;

import br.senac.qs.recursos.web.validacao.MinutoCheio;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Pedido de reserva (RF04): a sala é obrigatória; professor e até 10 materiais são opcionais e
 * entram na mesma reserva (tudo ou nada).
 */
public record CriarReservaRequest(
        @NotNull @Positive Long salaId,
        @Positive Long professorId,
        @Size(max = 10) List<@NotNull @Positive Long> materiaisIds,
        @NotNull @MinutoCheio OffsetDateTime inicio,
        @NotNull @MinutoCheio OffsetDateTime fim,
        @NotBlank @Size(max = 200) String finalidade) {

    @JsonIgnore
    @AssertTrue(message = "cada material deve aparecer uma vez só")
    public boolean isMateriaisSemRepeticao() {
        return materiaisIds == null || new HashSet<>(materiaisIds).size() == materiaisIds.size();
    }
}
