package br.senac.qs.recursos.web.recurso;

import java.time.OffsetDateTime;

import br.senac.qs.recursos.domain.model.PeriodoReserva;
import br.senac.qs.recursos.domain.model.TipoRecurso;
import br.senac.qs.recursos.domain.model.TipoSala;
import br.senac.qs.recursos.domain.pesquisa.FiltroRecursos;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

/**
 * Filtros da pesquisa (RF03), vindos da query string. Todos opcionais; a disponibilidade exige
 * {@code inicio} e {@code fim} juntos, com o fim depois do início.
 */
public record FiltroRecursosRequest(
        TipoRecurso tipo,
        @Positive Integer capacidadeMinima,
        @Size(max = 40) String bloco,
        Integer andar,
        TipoSala tipoSala,
        @Size(max = 80) String competencia,
        @Size(max = 60) String tipoMaterial,
        @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime inicio,
        @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime fim,
        @Min(0) Integer pagina,
        @Min(1) @Max(100) Integer tamanho) {

    static final int TAMANHO_PADRAO = 20;

    public FiltroRecursosRequest {
        pagina = pagina == null ? 0 : pagina;
        tamanho = tamanho == null ? TAMANHO_PADRAO : tamanho;
    }

    @AssertTrue(message = "informe início e fim juntos para pesquisar a disponibilidade")
    public boolean isPeriodoCompleto() {
        return (inicio == null) == (fim == null);
    }

    @AssertTrue(message = "o fim deve ser posterior ao início")
    public boolean isPeriodoValido() {
        return inicio == null || fim == null || fim.isAfter(inicio);
    }

    FiltroRecursos filtro() {
        var periodo = inicio == null ? null : new PeriodoReserva(inicio.toInstant(), fim.toInstant());
        return new FiltroRecursos(tipo, capacidadeMinima, bloco, andar, tipoSala, competencia, tipoMaterial, periodo);
    }
}
