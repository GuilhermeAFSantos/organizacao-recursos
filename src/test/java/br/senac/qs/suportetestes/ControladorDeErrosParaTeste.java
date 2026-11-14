package br.senac.qs.suportetestes;

import br.senac.qs.recursos.domain.exception.ConflitoHorarioException;
import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller usado SOMENTE em testes, para provocar cada tipo de erro e verificar a resposta.
 *
 * <p>Fica num pacote fora de {@code br.senac.qs.recursos} de propósito: assim o component scan
 * da aplicação não o encontra, e ele não aparece nos testes de integração completos.
 * O teste o registra explicitamente com {@code @Import}.
 */
@RestController
@RequestMapping("/teste-erros")
public class ControladorDeErrosParaTeste {

    public record SalaDeTeste(@NotBlank String nome, @Positive int capacidade) {
    }

    private final Validator validator;

    public ControladorDeErrosParaTeste(Validator validator) {
        this.validator = validator;
    }

    @GetMapping("/regra-negocio")
    void regraNegocio() {
        throw new RegraNegocioException("Reservas iniciadas não podem ser canceladas.");
    }

    @GetMapping("/conflito")
    void conflito() {
        throw new ConflitoHorarioException("A sala 101 já está reservada nesse horário.");
    }

    @GetMapping("/nao-encontrado")
    void naoEncontrado() {
        throw new RecursoNaoEncontradoException("Sala 999 não encontrada.");
    }

    @GetMapping("/concorrencia")
    void concorrencia() {
        throw new ObjectOptimisticLockingFailureException("br.senac.qs.recursos.domain.model.Reserva", 42L);
    }

    @GetMapping("/integridade")
    void integridade() {
        throw new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"uk_usuario_email\" SQL [insert into usuario]");
    }

    @GetMapping("/inesperado")
    void inesperado() {
        throw new IllegalStateException("detalhe interno: senha=123 em br.senac.qs.recursos.ClasseInterna");
    }

    @GetMapping("/restricao")
    void restricao() {
        throw new ConstraintViolationException(validator.validate(new SalaDeTeste(" ", 0)));
    }

    @PostMapping("/sala")
    SalaDeTeste criarSala(@Valid @RequestBody SalaDeTeste sala) {
        return sala;
    }

    @GetMapping("/parametro")
    int parametro(@RequestParam int numero) {
        return numero;
    }

    @GetMapping("/parametro-validado")
    int parametroValidado(@RequestParam @Min(1) int quantidade) {
        return quantidade;
    }

    @GetMapping("/somente-admin")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    String somenteAdmin() {
        return "ok";
    }
}
