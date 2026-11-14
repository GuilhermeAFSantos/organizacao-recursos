package br.senac.qs.recursos.web.relatorio;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import br.senac.qs.recursos.application.relatorio.GerarRelatorioUtilizacao;
import br.senac.qs.recursos.application.relatorio.RelatorioUtilizacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Relatórios de gestão (RF12), para RESPONSAVEL e ADMINISTRADOR. */
@RestController
@RequestMapping("/api/relatorios")
@PreAuthorize("hasAnyRole('RESPONSAVEL', 'ADMINISTRADOR')")
public class RelatorioController {

    static final int MAXIMO_DE_DIAS = 366;

    private final GerarRelatorioUtilizacao gerar;

    public RelatorioController(GerarRelatorioUtilizacao gerar) {
        this.gerar = gerar;
    }

    /** Período em dias (AAAA-MM-DD, horário de Brasília), com o fim incluído e no máximo 366 dias. */
    public record PeriodoRequest(
            @NotNull @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
            @NotNull @DateTimeFormat(iso = ISO.DATE) LocalDate fim) {

        @AssertTrue(message = "o fim não pode ser anterior ao início")
        public boolean isPeriodoValido() {
            return inicio == null || fim == null || !fim.isBefore(inicio);
        }

        @AssertTrue(message = "o período pode ter no máximo " + MAXIMO_DE_DIAS + " dias")
        public boolean isPeriodoDentroDoLimite() {
            return inicio == null || fim == null || ChronoUnit.DAYS.between(inicio, fim) + 1 <= MAXIMO_DE_DIAS;
        }
    }

    /** Ex.: {@code GET /api/relatorios/utilizacao?inicio=2026-12-01&fim=2026-12-31}. */
    @GetMapping("/utilizacao")
    public RelatorioUtilizacao utilizacao(@Valid @ModelAttribute PeriodoRequest periodo) {
        return gerar.gerar(periodo.inicio(), periodo.fim());
    }
}
