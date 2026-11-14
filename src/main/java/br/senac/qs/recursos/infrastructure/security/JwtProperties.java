package br.senac.qs.recursos.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do JWT, lida de {@code app.jwt.*}.
 *
 * <p>A validação acontece na construção: se o segredo estiver ausente ou curto demais, a
 * aplicação não sobe (falha rápida). HS256 exige chave de pelo menos 256 bits (32 bytes);
 * uma chave menor seria vulnerável a força bruta.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String segredo, Duration expiracao) {

    public static final int TAMANHO_MINIMO_SEGREDO_BYTES = 32;
    public static final Duration EXPIRACAO_PADRAO = Duration.ofHours(2);

    public JwtProperties {
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_SEGREDO_BYTES) {
            throw new IllegalArgumentException(
                    "A propriedade app.jwt.segredo (variável de ambiente JWT_SECRET) é obrigatória e deve ter "
                            + "pelo menos " + TAMANHO_MINIMO_SEGREDO_BYTES + " bytes (256 bits) para HS256.");
        }
        if (expiracao == null) {
            expiracao = EXPIRACAO_PADRAO;
        }
    }
}
