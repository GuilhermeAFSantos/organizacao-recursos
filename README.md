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
| Cobertura de linhas (JaCoCo) | ≥ 80% | **98,4%** |
| Cobertura de branches (JaCoCo) | ≥ 70% | **92,6%** |
| Bugs / vulnerabilidades (SonarCloud) | 0 | **0 / 0** |
| Requisitos críticos (RC) na RTM | 100% | **100%** (8 de 8 rastreados) |

Funcionalidades prontas: login com JWT, bloqueio após 5 senhas erradas e gestão de usuários (RF01);
cadastro de salas, professores e materiais com desativação (RF02); pesquisa por tipo, capacidade,
localização, competência e disponibilidade (RF03); reserva de sala com professor e materiais, tudo ou
nada, com conflito também na agenda do professor (RF04, RF05), e alteração de horário (RF04); retirada e devolução de materiais com pendências e histórico
imutável (RF09); notificação ao solicitante após cada mudança, simulada ou via serviço externo (RF11);
relatório de utilização, carga horária e conflitos evitados (RF12); interface web responsiva por perfil
(RF13);
com as regras RC01, RC02 e RC03; aprovação automática de recurso não restrito; aprovação, rejeição,
cancelamento, início de uso, não comparecimento e conclusão com a máquina de estados (RC05, RC06,
RC07) e auditoria de cada mudança de reservas, bloqueios, usuários e recursos (RC08, RF10); manutenção e bloqueios com encerramento antecipado (RF08,
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
| http://localhost:8080/ | Interface web: entre com o e-mail e a senha (o menu muda conforme o perfil) |
| http://localhost:8080/swagger-ui.html | Documentação interativa da API (RF14) |
| http://localhost:8080/actuator/health | Health check |

**Primeiro acesso:** ao subir, a aplicação cria o ADMINISTRADOR definido em `ADMIN_EMAIL` e
`ADMIN_SENHA` no `.env` (se ainda não houver nenhum). Para obter um token:

```bash
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
     -d '{"email": "admin@senac.br", "senha": "<ADMIN_SENHA>"}'
```

Use o `token` devolvido no cabeçalho `Authorization: Bearer …` (ou no botão "Authorize" do Swagger).
O administrador cadastra os demais usuários em `POST /api/usuarios` e os recursos em
`POST /api/salas`, `/api/professores` e `/api/materiais`; qualquer usuário logado pesquisa em
`GET /api/recursos` (ex.: `?tipoSala=LABORATORIO&capacidadeMinima=30&inicio=…&fim=…`).

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
| Unidade | `PeriodoReservaTest`, `StatusReservaTest`, `ReservaTest`, `BloqueioTest`, `UsuarioTest`, `CadastroRecursosTest`, `ReservaComVariosRecursosTest`, `AlteracaoReservaTest`, `MovimentacaoMaterialTest`, `NotificacaoTest`, `MinutoCheioValidatorTest`, `SenhaForteValidatorTest`, `GarantirPrimeiroAdministradorTest`, `CriarReservaTest`, `JwtPropertiesTest`, `TipoProblemaTest` | Regras puras (RC01, RC02, matriz de 49 transições, perfis, horários, auditoria), tradução de erros do banco, configuração |
| Web (MockMvc) | `GlobalExceptionHandlerTest` | Contrato de erros ProblemDetail, sem vazar detalhes internos |
| Arquitetura | `ArquiteturaTest` (ArchUnit) | Dependências entre camadas |
| Integração (banco) | `EsquemaBancoIT` | Constraints e triggers do schema no PostgreSQL real |
| API (caixa-preta) | `LoginIT`, `UsuariosIT`, `CadastroRecursosIT`, `PesquisaRecursosIT`, `CriarReservaSalaIT`, `ReservaComVariosRecursosIT`, `AlteracaoHorarioIT`, `RetiradaDevolucaoIT`, `NotificacaoIT` (WireMock), `ConflitosEvitadosIT`, `RelatorioUtilizacaoIT`, `ListagemReservasIT`, `InterfaceIT`, `TransicoesReservaIT`, `BloqueiosIT`, `AplicacaoIT` | HTTP + JWT + banco reais, ponta a ponta, incluindo aprovar × cancelar e reserva × manutenção simultâneos |
| Ponta a ponta | `FluxoCompletoIT` | Do primeiro login do administrador ao cadastro e à pesquisa da sala, à reserva concluída, ao bloqueio e à trilha de auditoria |
| Concorrência | `ReservaConcorrenciaIT`, `ReservaComVariosRecursosIT`, `AlteracaoHorarioIT` | RC03: 20 requisições simultâneas × 10 rodadas → exatamente 1 reserva e 19 conflitos evitados registrados; o mesmo professor pedido por 10 salas → 1; materiais em ordens opostas sem deadlock; alterações simultâneas sem dessincronizar |

## Estrutura

```
src/main/java/br/senac/qs/recursos
├── domain           regras de negócio (entidades, value objects, repositórios, critérios de pesquisa, exceções)
├── application      casos de uso transacionais
├── infrastructure   configuração e segurança (JWT)
└── web              controllers REST, DTOs e tratamento de erros
src/main/resources/db/migration   migrations Flyway (V1 schema, V2 RC07, V3 login, V4 nome de sala, V5 movimentações imutáveis)
```

As dependências entre camadas são verificadas no build pelo `ArquiteturaTest`. Detalhes em
[docs/arquitetura.md](docs/arquitetura.md).

## Integração contínua

A cada pull request e a cada push na `main`, o [workflow de CI](.github/workflows/ci.yml) roda
`./mvnw verify` (build, todos os testes, metas de cobertura) e a análise do SonarCloud com espera
pelo Quality Gate. Os relatórios de teste e cobertura ficam como artefato de cada execução.
