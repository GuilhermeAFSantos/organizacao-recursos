package br.senac.qs.recursos.application.usuario;

import java.time.Clock;

import br.senac.qs.recursos.domain.repository.RegistroAuditoriaRepository;
import br.senac.qs.recursos.domain.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class GarantirPrimeiroAdministrador {

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

    public void executar(String email, String senha) {
        throw new UnsupportedOperationException("ainda não implementado");
    }
}
