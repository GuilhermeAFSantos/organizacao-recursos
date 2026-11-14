package br.senac.qs.recursos.infrastructure.config;

import br.senac.qs.recursos.application.usuario.GarantirPrimeiroAdministrador;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Na inicialização, garante o primeiro ADMINISTRADOR a partir de {@code app.admin.*} (ADMIN_EMAIL/ADMIN_SENHA). */
@Component
public class PrimeiroAdministradorRunner implements ApplicationRunner {

    private final GarantirPrimeiroAdministrador garantir;
    private final String email;
    private final String senha;

    public PrimeiroAdministradorRunner(GarantirPrimeiroAdministrador garantir,
                                       @Value("${app.admin.email:}") String email,
                                       @Value("${app.admin.senha:}") String senha) {
        this.garantir = garantir;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        garantir.executar(email, senha);
    }
}
