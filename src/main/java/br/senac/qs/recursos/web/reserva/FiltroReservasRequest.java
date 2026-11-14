package br.senac.qs.recursos.web.reserva;

import java.time.LocalDate;

import br.senac.qs.recursos.domain.model.StatusReserva;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

/** Filtros da listagem de reservas, na query string. Dias de Brasília (AAAA-MM-DD), ambos incluídos. */
public record FiltroReservasRequest(
        StatusReserva status,
        @DateTimeFormat(iso = ISO.DATE) LocalDate de,
        @DateTimeFormat(iso = ISO.DATE) LocalDate ate,
        @Min(0) Integer pagina,
        @Min(1) @Max(100) Integer tamanho) {

    static final int TAMANHO_PADRAO = 50;

    public FiltroReservasRequest {
        pagina = pagina == null ? 0 : pagina;
        tamanho = tamanho == null ? TAMANHO_PADRAO : tamanho;
    }

    @AssertTrue(message = "o fim do período não pode ser anterior ao início")
    public boolean isPeriodoValido() {
        return de == null || ate == null || !ate.isBefore(de);
    }
}
