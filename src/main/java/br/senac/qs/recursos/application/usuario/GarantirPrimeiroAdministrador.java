package br.senac.qs.recursos.application.usuario;

import java.time.Clock;

import br.senac.qs.recursos.domain.model.Perfil;
import br.senac.qs.recursos.domain.model.PoliticaSenha;
import br.senac.qs.recursos.domain.model.RegistroAuditoria;
import br.senac.qs.recursos.domain.model.Usuario;
import br.senac.qs.recursos.domain.repository.RegistroAuditoriaRepository;
import br.senac.qs.recursos.domain.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante que exista um ADMINISTRADOR (sem ele, ninguém cadastra usuários). Chamado na
 * inicialização com ADMIN_EMAIL e ADMIN_SENHA do .env: nenhuma senha fica no código.
 *
 * <p>Configuração inválida (senha fraca, e-mail já usado) impede a aplicação de subir, com mensagem
 * clara: melhor falhar no deploy do que subir sem administrador ou com senha fraca.
 */
@Service
public class GarantirPrimeiroAdministrador {

    private static final Logger log = LoggerFactory.getLogger(GarantirPrimeiroAdministrador.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder senhas;
    private final RegistroAuditoriaRepository auditoria;
    private final Clock clock;

    public GarantirPrimeiroAdministrador(UsuarioRepository usuarios, PasswordEncoder senhas,
                                         RegistroAuditoriaRepository auditoria, Clock clock) {
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public void executar(String email, String senha) {
        if (usuarios.existsByPerfilAndAtivoTrue(Perfil.ADMINISTRADOR)) {
            return;
        }
        if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
            log.warn("Nenhum ADMINISTRADOR ativo e ADMIN_EMAIL/ADMIN_SENHA não configurados: "
                    + "ninguém conseguirá cadastrar usuários. Defina as duas variáveis no .env.");
            return;
        }
        if (!PoliticaSenha.atende(senha)) {
            throw new IllegalStateException("ADMIN_SENHA não atende à política de senha (" + PoliticaSenha.DESCRICAO + ").");
        }
        var normalizado = Usuario.normalizarEmail(email);
        if (usuarios.existsByEmailIgnoreCase(normalizado)) {
            throw new IllegalStateException("ADMIN_EMAIL (" + normalizado + ") já pertence a outro usuário.");
        }
        var agora = clock.instant();
        var administrador = usuarios.save(Usuario.cadastrar("Administrador", normalizado, senhas.encode(senha),
                Perfil.ADMINISTRADOR, agora));
        auditoria.save(RegistroAuditoria.doUsuario(administrador.getId(), "CRIACAO", administrador, agora));
        log.info("Primeiro ADMINISTRADOR criado: {}", normalizado);
    }
}
