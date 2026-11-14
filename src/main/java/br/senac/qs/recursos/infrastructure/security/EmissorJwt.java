package br.senac.qs.recursos.infrastructure.security;

import java.time.Clock;

import br.senac.qs.recursos.application.autenticacao.EmissorDeToken;
import br.senac.qs.recursos.domain.model.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** JWT HS256 com o e-mail como "subject" e o perfil na claim {@code perfil}, válido por {@code app.jwt.expiracao}. */
@Component
public class EmissorJwt implements EmissorDeToken {

    private final JwtEncoder encoder;
    private final JwtProperties propriedades;
    private final Clock clock;

    public EmissorJwt(JwtEncoder encoder, JwtProperties propriedades, Clock clock) {
        this.encoder = encoder;
        this.propriedades = propriedades;
        this.clock = clock;
    }

    @Override
    public TokenEmitido emitir(Usuario usuario) {
        var agora = clock.instant();
        var expiraEm = agora.plus(propriedades.expiracao());
        var claims = JwtClaimsSet.builder()
                .subject(usuario.getEmail())
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .claim(SecurityConfig.CLAIM_PERFIL, usuario.getPerfil().name())
                .build();
        var cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        return new TokenEmitido(encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue(), expiraEm);
    }
}
