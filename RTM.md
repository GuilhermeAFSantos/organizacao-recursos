# Matriz de Rastreabilidade de Requisitos (RTM)

Liga cada requisito ao **risco** que ele controla, aos **testes** que o verificam e à **evidência**
objetiva (relatórios do CI, SonarCloud, commits). Atualize esta matriz **no mesmo PR** que muda um
requisito ou seus testes (ver [CONTRIBUTING.md](CONTRIBUTING.md)).

**Legenda de status:** ✅ Coberto (implementado e testado) · 🟡 Parcial (parte implementada e
testada; o que falta está descrito) · ⏳ Pendente (não implementado; pode já ter schema ou cenários).

**Onde estão as evidências:**
- **CI:** cada execução do [workflow de CI](https://github.com/GuilhermeAFSantos/organizacao-recursos/actions/workflows/ci.yml)
  publica o artefato `relatorios-qualidade` (relatórios do Surefire/Failsafe e do JaCoCo).
- **SonarCloud:** [painel do projeto](https://sonarcloud.io/summary/overall?id=GuilhermeAFSantos_organizacao-recursos).
- **Commits:** os hashes abaixo levam ao histórico; os de TDD seguem a sequência *red → green → refactor*.

Testes citados como `Classe › Grupo` referem-se aos `@Nested`/`@DisplayName` de cada classe.

## Resumo

| Grupo | Total | ✅ | 🟡 | ⏳ |
|---|---|---|---|---|
| Requisitos funcionais (RF) | 14 | 2 | 6 | 6 |
| Regras críticas (RC) | 8 | 3 | 3 | 2 |
| Não funcionais e metas (RNF) | 8 | 6 | 1 | 1 |

Os 8 requisitos críticos estão **todos rastreados** nesta matriz (meta: 100%). Os pendentes já
têm cenários BDD escritos em [docs/testes/bdd-aprovacao-bloqueio.md](docs/testes/bdd-aprovacao-bloqueio.md).

## Regras críticas (RC)

| Requisito | Risco | Teste(s) | Evidência | Status |
|---|---|---|---|---|
| **RC01** O término deve ser posterior ao início | Reserva com duração zero ou negativa bloqueia agenda e distorce relatórios; fusos diferentes mascaram o erro | [`PeriodoReservaTest`](src/test/java/br/senac/qs/recursos/domain/model/PeriodoReservaTest.java) › RC01 (9 casos, incl. duração zero em fusos diferentes); [`CriarReservaSalaIT`](src/test/java/br/senac/qs/recursos/web/reserva/CriarReservaSalaIT.java) › RC01 (422 via API); [`EsquemaBancoIT`](src/test/java/br/senac/qs/recursos/infrastructure/persistence/EsquemaBancoIT.java) › RC01 (CHECK no banco) | TDD: [`f428d26`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/f428d26) red → [`bef838c`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/bef838c) green → [`c781308`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/c781308) refactor; relatórios do CI | ✅ |
| **RC02** Reservas do mesmo recurso não se sobrepõem; intervalos que só se tocam são válidos | Dupla alocação de sala, professor ou material; recusar indevidamente aulas em sequência (08–10 e 10–12) | `PeriodoReservaTest` › RC02 (11 casos, simetria A×B/B×A); `CriarReservaSalaIT` › RC02 (409 e 201 nas fronteiras); `EsquemaBancoIT` › constraint de exclusão (12 casos) | TDD: [`d7bd655`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/d7bd655) → [`ce8f35e`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/ce8f35e) → [`b9c5650`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/b9c5650); API: [`10d35fc`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/10d35fc) → [`6dac8f9`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/6dac8f9) → [`0714f54`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/0714f54) | ✅ (API para salas; a constraint do banco já vale para os 3 tipos de recurso) |
| **RC03** Duas solicitações simultâneas produzem uma única reserva | Corrida *check-then-insert*: duas reservas aceitas; deadlock devolvendo erro 500 | [`ReservaConcorrenciaIT`](src/test/java/br/senac/qs/recursos/web/reserva/ReservaConcorrenciaIT.java) (20 requisições simultâneas × 10 rodadas: exatamente 1×201 e 19×409, nenhum 500); [`CriarReservaSalaTest`](src/test/java/br/senac/qs/recursos/application/reserva/CriarReservaSalaTest.java) (tradução do 23P01) | [`7d6d655`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/7d6d655) red (recusas pela constraint medidas por rodada) → [`e1fca76`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/e1fca76); deadlock 40P01: [`d485436`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/d485436) red → [`de862b9`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/de862b9) green; [arquitetura §3.1](docs/arquitetura.md) | ✅ |
| **RC04** Recursos em manutenção não podem ser reservados | Reserva de sala interditada ou equipamento quebrado | `EsquemaBancoIT` › "RC04: reserva sobre período de manutenção é barrada pelo banco"; cenários MN-01 a MN-09 (BDD) | Relatórios do CI | 🟡 Garantia no banco ✅; falta a API de manutenção (RF08) e os testes MN |
| **RC05** Fluxo de estados SOLICITADA → APROVADA → EM_USO → CONCLUIDA (+ REJEITADA, CANCELADA, NAO_COMPARECEU); transições inválidas rejeitadas | Reserva "ressuscitada" ou que pula a aprovação | Cenários ES-01 a ES-05 (BDD), incluindo a matriz de 49 pares | [Cenários BDD](docs/testes/bdd-aprovacao-bloqueio.md) | ⏳ Enum `StatusReserva` e CHECK no banco prontos; máquina de estados pendente |
| **RC06** Somente RESPONSAVEL aprova recursos restritos | Autoaprovação; escalada de privilégio | Cenários AP-05, AU-01 a AU-04 (BDD) | [Cenários BDD](docs/testes/bdd-aprovacao-bloqueio.md) | ⏳ |
| **RC07** Reservas iniciadas não podem ser apagadas | Perda de histórico de uso e de evidência para auditoria | `EsquemaBancoIT` › RC07 (DELETE bloqueado nos 7 estados + TRUNCATE bloqueado) | TDD: [`eb0172a`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/eb0172a) red → [`642f921`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/642f921) green (migration V2) | 🟡 Nenhuma reserva é apagada ✅; "EM_USO não pode ser cancelada" depende da máquina de estados (RC05) |
| **RC08** Toda mudança de estado gera auditoria (quem, quando, estado anterior, novo, motivo) | Alterações sem rastro; adulteração do histórico | `EsquemaBancoIT` › RC08 (INSERT permitido; UPDATE e DELETE bloqueados por trigger) | Relatórios do CI | 🟡 Histórico imutável ✅; gravação do registro em cada transição pendente (RC05) |

## Requisitos funcionais (RF)

| Requisito | Risco | Teste(s) | Evidência | Status |
|---|---|---|---|---|
| **RF01** Autenticação e autorização por perfil | Acesso indevido; token forjado; perfil sem permissão executando operação | [`AplicacaoIT`](src/test/java/br/senac/qs/recursos/AplicacaoIT.java) (401 sem token, token de outra chave, token válido, sem cookie de sessão); [`GlobalExceptionHandlerTest`](src/test/java/br/senac/qs/recursos/web/error/GlobalExceptionHandlerTest.java) › Autenticação e autorização; [`JwtPropertiesTest`](src/test/java/br/senac/qs/recursos/infrastructure/security/JwtPropertiesTest.java) (segredo fraco impede subir); `CriarReservaSalaIT` › perfil RESPONSAVEL → 403 | Relatórios do CI; SonarCloud java:S4502 analisado ([`838e75a`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/838e75a)) | 🟡 Validação de JWT e perfis ✅; falta endpoint de login e gestão de usuários |
| **RF02** Cadastro e consulta de salas, professores e materiais | Recursos duplicados ou inconsistentes | `EsquemaBancoIT` (schema) | Migration V1 | ⏳ Schema pronto; API pendente |
| **RF03** Pesquisa por tipo, capacidade, localização, competência e disponibilidade | Usuário não encontra recurso adequado; consulta lenta | — | Índices na migration V1 | ⏳ |
| **RF04** Criação, alteração e cancelamento de reservas | Reserva inválida gravada; alteração que fura as regras | `CriarReservaSalaIT` (criação: 201, 404, 400, 403, mass assignment) | [PR #2](https://github.com/GuilhermeAFSantos/organizacao-recursos/pull/2) | 🟡 Criação de reserva de sala ✅; alteração, cancelamento e outros recursos pendentes |
| **RF05** Detecção de sobreposição, inclusive na agenda do professor | Professor alocado em duas aulas ao mesmo tempo | Ver RC02 | Ver RC02 | 🟡 Salas ✅; reserva com professor pendente (a constraint já cobre o tipo PROFESSOR) |
| **RF06** Proteção contra dupla reserva em solicitações simultâneas | Ver RC03 | Ver RC03 | Ver RC03 | ✅ |
| **RF07** Aprovação obrigatória para recursos restritos | Uso de laboratório ou professor sem autorização | Cenários AP-01 a AP-07 (BDD) | [Cenários BDD](docs/testes/bdd-aprovacao-bloqueio.md) | ⏳ |
| **RF08** Bloqueio de recursos em manutenção | Ver RC04 | Ver RC04 | Ver RC04 | 🟡 Garantia no banco ✅; API pendente |
| **RF09** Registro de retirada e devolução de materiais | Equipamento sem responsável; dano não registrado | — | Tabela `movimentacao_material` (V1) | ⏳ |
| **RF10** Histórico auditável de mudanças | Ver RC08 | Ver RC08 | Ver RC08 | 🟡 |
| **RF11** Notificação simulada ou integração externa (WireMock nos testes) | Falha externa desfazer a reserva; usuário não avisado | — | Dependência WireMock no `pom.xml`; decisão D9 em [arquitetura](docs/arquitetura.md) | ⏳ |
| **RF12** Relatório de utilização, carga horária e conflitos evitados | Indicadores errados para gestão | — | Tabela `conflito_evitado` (V1) | ⏳ |
| **RF13** Interface responsiva e mensagens de erro compreensíveis | Usuário não entende o erro; detalhe interno vazado | `GlobalExceptionHandlerTest` (20 testes: domínio, validação, protocolo HTTP, persistência, erro inesperado); [`TipoProblemaTest`](src/test/java/br/senac/qs/recursos/web/error/TipoProblemaTest.java); `AplicacaoIT` › página inicial | Relatórios do CI | 🟡 Mensagens de erro ✅; interface ainda só com página inicial |
| **RF14** Documentação da API | Integração errada por contrato desconhecido | `AplicacaoIT` › OpenAPI publicado e Swagger UI acessível | `/swagger-ui.html`, `/v3/api-docs` | ✅ (gerada a partir do código; acompanha cada novo endpoint) |

## Não funcionais e metas da disciplina (RNF)

| Requisito | Risco | Teste(s) / ferramenta | Evidência | Status |
|---|---|---|---|---|
| **RNF01** Cobertura ≥ 80% linhas e ≥ 70% branches, com build falhando abaixo | Código não testado em produção | JaCoCo `check` na fase `verify` (unidade + integração) | 97,4% linhas / 95,3% branches; [SonarCloud](https://sonarcloud.io/component_measures?id=GuilhermeAFSantos_organizacao-recursos&metric=coverage) | ✅ |
| **RNF02** 0 bugs e 0 vulnerabilidades críticas | Falhas de segurança e confiabilidade | SonarCloud com Quality Gate no CI | 0 bugs / 0 vulnerabilidades; correções [`838e75a`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/838e75a) (S4502) e [`76f159f`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/76f159f) (S6437) | ✅ |
| **RNF03** Validação de entrada | Dados malformados; *mass assignment* | `GlobalExceptionHandlerTest` › Validação de entrada; `CriarReservaSalaIT` (campos obrigatórios, horário sem fuso, campo `status` proibido) | Relatórios do CI | ✅ |
| **RNF04** Tratamento seguro de erros (ProblemDetail, RFC 9457, sem stack trace) | Vazamento de SQL, classes ou segredos | `GlobalExceptionHandlerTest` › Erro inesperado e Persistência | Relatórios do CI | ✅ |
| **RNF05** Autorização por perfil | Escalada de privilégio | Ver RF01 e RC06 | Ver RF01 | 🟡 |
| **RNF06** Desempenho sob carga (JMeter) | Degradação ou erros com muitos usuários | — | — | ⏳ |
| **RNF07** Arquitetura em camadas respeitada | Regras de negócio acopladas a HTTP ou banco | [`ArquiteturaTest`](src/test/java/br/senac/qs/recursos/ArquiteturaTest.java) (ArchUnit) | Relatórios do CI | ✅ |
| **RNF08** Segredos fora do código | Credenciais expostas no repositório | SonarCloud java:S6437; `.env` fora do git | [`76f159f`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/76f159f) | ✅ |
