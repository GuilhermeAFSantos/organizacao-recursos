package br.senac.qs.recursos.web.recurso;

import java.util.List;

import br.senac.qs.recursos.application.Pagina;
import br.senac.qs.recursos.application.recurso.ConsultarRecursos;
import br.senac.qs.recursos.application.recurso.DadosMaterial;
import br.senac.qs.recursos.application.recurso.DadosProfessor;
import br.senac.qs.recursos.application.recurso.DadosSala;
import br.senac.qs.recursos.application.recurso.GerenciarRecursos;
import br.senac.qs.recursos.application.recurso.RecursoResumo;
import br.senac.qs.recursos.domain.model.EstadoConservacao;
import br.senac.qs.recursos.domain.model.TipoSala;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Salas, professores e materiais (RF02). Cadastro, alteração e desativação são do ADMINISTRADOR;
 * a consulta é aberta a qualquer usuário logado. Não há exclusão: um recurso com histórico de
 * reservas é desativado, e o histórico continua íntegro (RC07, RC08).
 */
@RestController
@RequestMapping("/api")
public class RecursoController {

    private static final String ADMINISTRADOR = "hasRole('ADMINISTRADOR')";

    private final GerenciarRecursos gerenciar;
    private final ConsultarRecursos consultar;

    public RecursoController(GerenciarRecursos gerenciar, ConsultarRecursos consultar) {
        this.gerenciar = gerenciar;
        this.consultar = consultar;
    }

    public record SalaRequest(
            @NotBlank @Size(max = 120) String nome,
            @NotNull Boolean restrito,
            @NotNull @Positive Integer capacidade,
            @NotBlank @Size(max = 40) String bloco,
            @NotNull Integer andar,
            @NotNull TipoSala tipoSala) {

        DadosSala dados() {
            return new DadosSala(nome, restrito, capacidade, bloco, andar, tipoSala);
        }
    }

    public record ProfessorRequest(
            @NotBlank @Size(max = 120) String nome,
            @NotNull Boolean restrito,
            @NotBlank @Size(max = 30) String matricula,
            @Email @Size(max = 160) String email,
            @Size(max = 20) List<@NotBlank @Size(max = 80) String> competencias) {

        public ProfessorRequest {
            email = email == null ? null : email.strip();
        }

        DadosProfessor dados() {
            return new DadosProfessor(nome, restrito, matricula, email, competencias);
        }
    }

    public record MaterialRequest(
            @NotBlank @Size(max = 120) String nome,
            @NotNull Boolean restrito,
            @NotBlank @Size(max = 40) String patrimonio,
            @NotBlank @Size(max = 60) String tipoMaterial,
            @NotNull EstadoConservacao estadoConservacao) {

        DadosMaterial dados() {
            return new DadosMaterial(nome, restrito, patrimonio, tipoMaterial, estadoConservacao);
        }
    }

    @PostMapping("/salas")
    @PreAuthorize(ADMINISTRADOR)
    public ResponseEntity<RecursoResponse> cadastrarSala(@Valid @RequestBody SalaRequest requisicao,
                                                         @AuthenticationPrincipal Jwt jwt) {
        return criado(gerenciar.cadastrarSala(jwt.getSubject(), requisicao.dados()));
    }

    @PutMapping("/salas/{id}")
    @PreAuthorize(ADMINISTRADOR)
    public RecursoResponse alterarSala(@PathVariable Long id, @Valid @RequestBody SalaRequest requisicao,
                                       @AuthenticationPrincipal Jwt jwt) {
        return RecursoResponse.de(gerenciar.alterarSala(jwt.getSubject(), id, requisicao.dados()));
    }

    @PostMapping("/professores")
    @PreAuthorize(ADMINISTRADOR)
    public ResponseEntity<RecursoResponse> cadastrarProfessor(@Valid @RequestBody ProfessorRequest requisicao,
                                                              @AuthenticationPrincipal Jwt jwt) {
        return criado(gerenciar.cadastrarProfessor(jwt.getSubject(), requisicao.dados()));
    }

    @PutMapping("/professores/{id}")
    @PreAuthorize(ADMINISTRADOR)
    public RecursoResponse alterarProfessor(@PathVariable Long id, @Valid @RequestBody ProfessorRequest requisicao,
                                            @AuthenticationPrincipal Jwt jwt) {
        return RecursoResponse.de(gerenciar.alterarProfessor(jwt.getSubject(), id, requisicao.dados()));
    }

    @PostMapping("/materiais")
    @PreAuthorize(ADMINISTRADOR)
    public ResponseEntity<RecursoResponse> cadastrarMaterial(@Valid @RequestBody MaterialRequest requisicao,
                                                             @AuthenticationPrincipal Jwt jwt) {
        return criado(gerenciar.cadastrarMaterial(jwt.getSubject(), requisicao.dados()));
    }

    @PutMapping("/materiais/{id}")
    @PreAuthorize(ADMINISTRADOR)
    public RecursoResponse alterarMaterial(@PathVariable Long id, @Valid @RequestBody MaterialRequest requisicao,
                                           @AuthenticationPrincipal Jwt jwt) {
        return RecursoResponse.de(gerenciar.alterarMaterial(jwt.getSubject(), id, requisicao.dados()));
    }

    @PostMapping("/recursos/{id}/desativacao")
    @PreAuthorize(ADMINISTRADOR)
    public RecursoResponse desativar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return RecursoResponse.de(gerenciar.desativar(jwt.getSubject(), id));
    }

    @PostMapping("/recursos/{id}/reativacao")
    @PreAuthorize(ADMINISTRADOR)
    public RecursoResponse reativar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return RecursoResponse.de(gerenciar.reativar(jwt.getSubject(), id));
    }

    /** Pesquisa (RF03). Ex.: {@code GET /api/recursos?tipo=SALA&capacidadeMinima=30&inicio=...&fim=...} */
    @GetMapping("/recursos")
    public Pagina<RecursoResponse> pesquisar(@Valid @ModelAttribute FiltroRecursosRequest filtros) {
        return consultar.pesquisar(filtros.filtro(), filtros.pagina(), filtros.tamanho())
                .converter(RecursoResponse::de);
    }

    @GetMapping("/recursos/{id}")
    public RecursoResponse buscar(@PathVariable Long id) {
        return RecursoResponse.de(consultar.buscar(id));
    }

    private static ResponseEntity<RecursoResponse> criado(RecursoResumo recurso) {
        var local = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/recursos/{id}").buildAndExpand(recurso.id()).toUri();
        return ResponseEntity.created(local).body(RecursoResponse.de(recurso));
    }
}
