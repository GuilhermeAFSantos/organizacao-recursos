package br.senac.qs.recursos.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Configuração do JWT: falha rápida com segredo fraco")
class JwtPropertiesTest {

    private static final String SEGREDO_32_BYTES = "0123456789abcdef0123456789abcdef";

    @ParameterizedTest(name = "segredo [{0}] é rejeitado")
    @NullAndEmptySource
    @ValueSource(strings = {"curto", "0123456789abcdef0123456789abcde" /* 31 bytes: um a menos que o mínimo */})
    @DisplayName("segredo ausente ou com menos de 256 bits impede a aplicação de subir")
    void rejeitaSegredoFraco(String segredo) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtProperties(segredo, Duration.ofHours(1)))
                .withMessageContaining("JWT_SECRET")
                .withMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("segredo com exatamente 32 bytes é aceito (limite inferior)")
    void aceitaSegredoNoLimite() {
        assertThat(new JwtProperties(SEGREDO_32_BYTES, Duration.ofMinutes(30)).expiracao())
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("o tamanho é medido em BYTES (UTF-8), não em caracteres")
    void medeTamanhoEmBytes() {
        String dezesseisCaracteresDeDoisBytes = "é".repeat(16); // 16 caracteres, 32 bytes
        assertThat(new JwtProperties(dezesseisCaracteresDeDoisBytes, null).segredo()).hasSize(16);
    }

    @Test
    @DisplayName("sem expiração configurada, usa o padrão de 2 horas")
    void expiracaoPadrao() {
        assertThat(new JwtProperties(SEGREDO_32_BYTES, null).expiracao()).isEqualTo(JwtProperties.EXPIRACAO_PADRAO);
    }
}
