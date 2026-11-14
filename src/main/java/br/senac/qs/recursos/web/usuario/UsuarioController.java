package br.senac.qs.recursos.web.usuario;

import java.util.List;

import br.senac.qs.recursos.application.usuario.GerenciarUsuarios;
import br.senac.qs.recursos.application.usuario.UsuarioResumo;
import br.senac.qs.recursos.domain.model.Perfil;
import br.senac.qs.recursos.web.validacao.SenhaForte;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Usuários (RF01). Gestão exclusiva do ADMINISTRADOR; troca da própria senha para qualquer usuário logado. */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private static final String ADMINISTRADOR = "hasRole('ADMINISTRADOR')";

    private final GerenciarUsuarios usuarios;

    public UsuarioController(GerenciarUsuarios usuarios) {
        this.usuarios = usuarios;
    }

    public record CadastrarUsuarioRequest(
            @NotBlank @Size(max = 120) String nome,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @SenhaForte String senha,
            @NotNull Perfil perfil) {

        public CadastrarUsuarioRequest {
            email = email == null ? null : email.strip();
        }
    }

    public record RedefinirSenhaRequest(@NotBlank @SenhaForte String novaSenha) {
    }

    public record AlterarSenhaRequest(@NotBlank String senhaAtual, @NotBlank @SenhaForte String novaSenha) {
    }

    @PostMapping
    @PreAuthorize(ADMINISTRADOR)
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResumo cadastrar(@Valid @RequestBody CadastrarUsuarioRequest requisicao,
                                   @AuthenticationPrincipal Jwt jwt) {
        return usuarios.cadastrar(jwt.getSubject(), requisicao.nome(), requisicao.email(), requisicao.senha(),
                requisicao.perfil());
    }

    @GetMapping
    @PreAuthorize(ADMINISTRADOR)
    public List<UsuarioResumo> listar() {
        return usuarios.listar();
    }

    @PostMapping("/{id}/desativacao")
    @PreAuthorize(ADMINISTRADOR)
    public UsuarioResumo desativar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return usuarios.desativar(jwt.getSubject(), id);
    }

    @PutMapping("/{id}/senha")
    @PreAuthorize(ADMINISTRADOR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@PathVariable Long id, @Valid @RequestBody RedefinirSenhaRequest requisicao,
                               @AuthenticationPrincipal Jwt jwt) {
        usuarios.redefinirSenha(jwt.getSubject(), id, requisicao.novaSenha());
    }

    @PutMapping("/me/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterarPropriaSenha(@Valid @RequestBody AlterarSenhaRequest requisicao,
                                    @AuthenticationPrincipal Jwt jwt) {
        usuarios.alterarPropriaSenha(jwt.getSubject(), requisicao.senhaAtual(), requisicao.novaSenha());
    }
}
