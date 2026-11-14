# Organização de Recursos

[![CI](https://github.com/GuilhermeAFSantos/organizacao-recursos/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/GuilhermeAFSantos/organizacao-recursos/actions/workflows/ci.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=GuilhermeAFSantos_organizacao-recursos&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=GuilhermeAFSantos_organizacao-recursos)
[![Cobertura](https://sonarcloud.io/api/project_badges/measure?project=GuilhermeAFSantos_organizacao-recursos&metric=coverage)](https://sonarcloud.io/component_measures?id=GuilhermeAFSantos_organizacao-recursos&metric=coverage)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=GuilhermeAFSantos_organizacao-recursos&metric=bugs)](https://sonarcloud.io/project/issues?id=GuilhermeAFSantos_organizacao-recursos&types=BUG)
[![Vulnerabilidades](https://sonarcloud.io/api/project_badges/measure?project=GuilhermeAFSantos_organizacao-recursos&metric=vulnerabilities)](https://sonarcloud.io/project/issues?id=GuilhermeAFSantos_organizacao-recursos&types=VULNERABILITY)

API e interface responsiva para **alocar salas, professores e materiais sem conflitos de horário**.
Projeto da disciplina Qualidade de Software (Senac, 2026.2). Aqui, além de funcionar, o sistema
precisa **demonstrar** qualidade, segurança, rastreabilidade e desempenho com evidências objetivas.

| Documento | Conteúdo |
|---|---|
| [RTM.md](RTM.md) | Matriz de rastreabilidade: requisito → risco → teste → evidência → status |
| [docs/arquitetura.md](docs/arquitetura.md) | Camadas, modelo de domínio, estratégia anti-sobreposição, decisões e riscos |
| [docs/diagrama-contexto.md](docs/diagrama-contexto.md) | Diagrama de contexto do sistema |
| [docs/sequencia-criar-reserva.md](docs/sequencia-criar-reserva.md) | Sequência da criação de reserva com controle de concorrência |
| [docs/testes/bdd-aprovacao-bloqueio.md](docs/testes/bdd-aprovacao-bloqueio.md) | Cenários BDD da próxima fatia (aprovação, manutenção, estados) |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Fluxo de trabalho da equipe: branches, PRs, revisão e commits |

## Stack

Java 21 · Spring Boot 3.5 · Maven · PostgreSQL 17 · Spring Data JPA · Flyway · Spring Security
(JWT) · Bean Validation · springdoc-openapi · JUnit 5 · Mockito · Testcontainers · WireMock ·
ArchUnit · JaCoCo · SonarCloud · GitHub Actions.

## Situação atual

| Métrica | Meta | Atual |
|---|---|---|
| Cobertura de linhas (JaCoCo) | ≥ 80% | **98,0%** |
| Cobertura de branches (JaCoCo) | ≥ 70% | **95,0%** |
| Bugs / vulnerabilidades (SonarCloud) | 0 | **0 / 0** |
| Requisitos críticos (RC) na RTM | 100% | **100%** (8 de 8 rastreados) |

Funcionalidades prontas: autenticação JWT (validação de token e perfis); criação de reserva de sala
com as regras RC01, RC02 e RC03; aprovação automática de recurso não restrito; aprovação, rejeição,
cancelamento, início de uso, não comparecimento e conclusão com a máquina de estados (RC05, RC06,
RC07) e auditoria de cada mudança (RC08); manutenção e bloqueios com encerramento antecipado (RF08,
RC04); horários em minutos cheios; tratamento de erros em
ProblemDetail e Swagger. O restante está na [RTM](RTM.md), com status por requisito.

## Como rodar localmente

**Pré-requisitos:** JDK 21 e Docker (Docker Desktop no Windows). O Maven não precisa estar
instalado: use o wrapper `./mvnw` (no Windows, `mvnw.cmd`).

```bash
# 1. Configuração local (o .env NÃO vai para o git)
cp .env.exemplo .env          # depois troque DB_PASSWORD e JWT_SECRET
                              # segredo aleatório: openssl rand -base64 48

# 2. Banco de dados
docker compose up -d

# 3. Aplicação (perfil "local", que lê o .env)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

| Endereço | O que é |
|---|---|
| http://localhost:8080/ | Página inicial |
| http://localhost:8080/swagger-ui.html | Documentação interativa da API (RF14) |
| http://localhost:8080/actuator/health | Health check |

As rotas `/api/**` exigem um JWT no cabeçalho `Authorization: Bearer …`. O endpoint de login ainda
não existe (RF01 parcial, ver RTM); até lá, os testes geram tokens assinados com a chave da aplicação.

> **Se a aplicação não subir:** confira se o `.env` tem `DB_PASSWORD` e `JWT_SECRET` (com pelo menos
> 32 bytes). Se você mudou a senha depois de subir o banco, recrie o volume com
> `docker compose down -v && docker compose up -d`.

## Como rodar os testes

```bash
./mvnw test      # só testes de unidade (*Test): rápidos, não precisam de Docker
./mvnw verify    # todos: unidade + integração (*IT, com Testcontainers) + metas de cobertura
```

- `verify` **falha o build** se a cobertura ficar abaixo de 80% (linhas) ou 70% (branches).
- Os testes de integração sobem um **PostgreSQL real e descartável** via Testcontainers. Por isso o
  Docker precisa estar rodando. Não usamos H2: a constraint de exclusão, o `tstzrange` e os triggers
  só existem no PostgreSQL.
- Relatório de cobertura: `target/site/jacoco/index.html`.
- Rodar testes específicos (sem a verificação de cobertura, que reprovaria uma execução parcial):
  `./mvnw verify -Dtest=PeriodoReservaTest -Dit.test=ReservaConcorrenciaIT -Djacoco.skip=true`.

> **Erro "Unresolved compilation problems" com o VS Code aberto?** A extensão Java compila na mesma
> pasta `target/`. Rode `./mvnw clean verify`.

| Tipo | Classes | O que provam |
|---|---|---|
| Unidade | `PeriodoReservaTest`, `StatusReservaTest`, `ReservaTest`, `BloqueioTest`, `MinutoCheioValidatorTest`, `CriarReservaSalaTest`, `JwtPropertiesTest`, `TipoProblemaTest` | Regras puras (RC01, RC02, matriz de 49 transições, perfis, horários, auditoria), tradução de erros do banco, configuração |
| Web (MockMvc) | `GlobalExceptionHandlerTest` | Contrato de erros ProblemDetail, sem vazar detalhes internos |
| Arquitetura | `ArquiteturaTest` (ArchUnit) | Dependências entre camadas |
| Integração (banco) | `EsquemaBancoIT` | Constraints e triggers do schema no PostgreSQL real |
| API (caixa-preta) | `CriarReservaSalaIT`, `TransicoesReservaIT`, `BloqueiosIT`, `AplicacaoIT` | HTTP + JWT + banco reais, ponta a ponta, incluindo aprovar × cancelar e reserva × manutenção simultâneos |
| Concorrência | `ReservaConcorrenciaIT` | RC03: 20 requisições simultâneas × 10 rodadas → exatamente 1 reserva |

## Estrutura

```
src/main/java/br/senac/qs/recursos
├── domain           regras de negócio (entidades, value objects, repositórios, exceções)
├── application      casos de uso transacionais
├── infrastructure   configuração e segurança (JWT)
└── web              controllers REST, DTOs e tratamento de erros
src/main/resources/db/migration   migrations Flyway (V1 schema, V2 RC07)
```

As dependências entre camadas são verificadas no build pelo `ArquiteturaTest`. Detalhes em
[docs/arquitetura.md](docs/arquitetura.md).

## Integração contínua

A cada pull request e a cada push na `main`, o [workflow de CI](.github/workflows/ci.yml) roda
`./mvnw verify` (build, todos os testes, metas de cobertura) e a análise do SonarCloud com espera
pelo Quality Gate. Os relatórios de teste e cobertura ficam como artefato de cada execução.
