package br.senac.qs.recursos.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O "agora" do sistema é um bean injetável, nunca {@code Instant.now()} espalhado pelo código.
 * Assim as regras que dependem do horário atual (ex.: não cancelar reserva já iniciada)
 * podem ser testadas de forma determinística com {@code Clock.fixed(...)}.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
