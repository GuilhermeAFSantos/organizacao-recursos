package br.senac.qs.recursos.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Autenticação e autorização (RF01).
 *
 * <ul>
 *   <li><b>Stateless:</b> sem sessão no servidor; cada requisição traz o JWT no cabeçalho
 *       {@code Authorization: Bearer ...}.</li>
 *   <li><b>CSRF desabilitado:</b> o ataque CSRF depende de credenciais enviadas automaticamente
 *       pelo navegador (cookies). Como o token vai num cabeçalho definido pelo JavaScript, o
 *       navegador não o envia sozinho, e a proteção não se aplica.</li>
 *   <li><b>Negado por padrão:</b> só as rotas listadas em {@link #ROTAS_PUBLICAS} são abertas.</li>
 *   <li><b>Perfis:</b> a claim {@code perfil} do token vira a autoridade {@code ROLE_<PERFIL>},
 *       usada com {@code @PreAuthorize("hasRole('RESPONSAVEL')")} nos casos de uso.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    public static final String CLAIM_PERFIL = "perfil";

    static final String[] ROTAS_PUBLICAS = {
        "/", "/index.html", "/*.html", "/favicon.ico", "/css/**", "/js/**", "/img/**",
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
        "/actuator/health",
        "/api/auth/**",
        "/error"
    };

    /*
     * SonarCloud java:S4502 ("CSRF desabilitado") analisado e considerado SEGURO aqui:
     * a API é stateless e não usa cookie de autenticação; o JWT só viaja no cabeçalho Authorization,
     * que o navegador não anexa sozinho a requisições forjadas por outro site.
     * Evidência automatizada: AplicacaoIT#naoCriaSessaoNemCookie.
     * Se um dia a autenticação passar a usar cookie, a proteção CSRF DEVE ser reativada.
     */
    @SuppressWarnings("java:S4502")
    @Bean
    SecurityFilterChain filtroSeguranca(HttpSecurity http, ProblemDetailSecurityHandler respostasDeErro)
            throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(regras -> regras
                .requestMatchers(ROTAS_PUBLICAS).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(recurso -> recurso
                .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDePerfis()))
                .authenticationEntryPoint(respostasDeErro)
                .accessDeniedHandler(respostasDeErro))
            .exceptionHandling(excecoes -> excecoes
                .authenticationEntryPoint(respostasDeErro)
                .accessDeniedHandler(respostasDeErro));
        return http.build();
    }

    static JwtAuthenticationConverter conversorDePerfis() {
        var autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName(CLAIM_PERFIL);
        autoridades.setAuthorityPrefix("ROLE_");
        var conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(autoridades);
        return conversor;
    }

    /** BCrypt: hash lento e com salt; senhas nunca são guardadas em texto puro. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
