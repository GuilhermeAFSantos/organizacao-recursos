package br.senac.qs.recursos.suporte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL REAL e descartável para os testes de integração (nada de H2).
 *
 * <p>Mesma imagem do docker-compose: a constraint de exclusão, o {@code tstzrange}, os triggers
 * e o {@code btree_gist} só existem no PostgreSQL. Um banco em memória diferente faria os
 * testes passarem sem provar nada sobre o banco de produção.
 *
 * <p>{@code @ServiceConnection} faz o Spring Boot apontar o DataSource para o container
 * automaticamente (URL, usuário e senha gerados na hora).
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {

    public static final String IMAGEM_POSTGRES = "postgres:17-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse(IMAGEM_POSTGRES));
    }
}
