package br.senac.qs.recursos.web.autenticacao;

import java.time.Instant;

import br.senac.qs.recursos.application.autenticacao.Autenticar;
import br.senac.qs.recursos.domain.model.Perfil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login (RF01). Rota pública (ver {@code SecurityConfig.ROTAS_PUBLICAS}). */
@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final Autenticar autenticar;

    public AutenticacaoController(Autenticar autenticar) {
        this.autenticar = autenticar;
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String senha) {

        /** Tira espaços das pontas ANTES da validação: " Ana@senac.br " é um e-mail válido digitado com espaço. */
        public LoginRequest {
            email = email == null ? null : email.strip();
        }
    }

    public record LoginResponse(String token, String tipo, Instant expiraEm, Perfil perfil) {
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest requisicao) {
        var sessao = autenticar.executar(requisicao.email(), requisicao.senha());
        return new LoginResponse(sessao.token(), "Bearer", sessao.expiraEm(), sessao.perfil());
    }
}
