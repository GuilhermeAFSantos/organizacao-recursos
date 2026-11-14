package br.senac.qs.recursos.suporte;

import java.time.Duration;
import java.time.Instant;

import br.senac.qs.recursos.infrastructure.security.SecurityConfig;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Gera JWTs assinados com a mesma chave da aplicação, para testes que fazem requisições HTTP
 * reais. (Quando o endpoint de login existir, os testes ponta a ponta passarão a usá-lo.)
 */
public final class GeradorTokenTeste {

    private GeradorTokenTeste() {
    }

    public static String token(JwtEncoder encoder, String email, String perfil) {
        var agora = Instant.now();
        var claims = JwtClaimsSet.builder()
                .subject(email)
                .issuedAt(agora)
                .expiresAt(agora.plus(Duration.ofMinutes(10)))
                .claim(SecurityConfig.CLAIM_PERFIL, perfil)
                .build();
        var cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
    }
}
