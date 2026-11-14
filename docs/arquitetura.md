# Arquitetura e decisões de projeto

Registro das decisões da Fase 1. Cada decisão traz o **porquê**, para que qualquer integrante
consiga defendê-la. Mudou alguma decisão? Atualize este arquivo no mesmo PR.

## 1. Camadas e pacotes

```
br.senac.qs.recursos
├── domain            regras de negócio; não conhece HTTP
│   ├── model         entidades, value objects e enums
│   ├── repository    interfaces Spring Data
│   ├── pesquisa      critérios de pesquisa (value objects usados pela web e pelos repositórios)
│   └── exception     exceções com mensagem para o usuário final (pt-BR)
├── application       casos de uso (@Transactional) e portas de saída (ex.: NotificadorPort)
├── infrastructure    configuração, segurança (JWT) e adaptadores (cliente HTTP de notificação)
└── web               controllers REST, DTOs (records) e handler global de erros (ProblemDetail)
```

Dependências permitidas: `web → application → domain`; `infrastructure` pode ver todas; `domain` não
depende de nenhuma. **Verificado no build** por `ArquiteturaTest` (ArchUnit).

- **Regras puras fora do Spring.** RC01, RC05 e a detecção de sobreposição ficam em objetos simples,
  testados com testes unitários parametrizados, sem mock.
- **Inversão de dependência só na integração externa.** `application` define `NotificadorPort`;
  `infrastructure` o implementa. Nos testes, o WireMock faz o papel do servidor externo.
- **Hexagonal pragmática.** Entidades com anotações JPA e repositórios Spring Data no `domain`. A
  versão "pura" exigiria dois modelos de cada entidade (domínio e persistência): custo alto para
  uma equipe de 4, com ganho pequeno neste projeto.
- **Controllers nunca expõem entidades**, só DTOs. Junto com `fail-on-unknown-properties`, isso
  impede *mass assignment* (ex.: o cliente enviar `"status": "APROVADA"`).

## 2. Modelo de domínio

```mermaid
classDiagram
    direction LR
    class Recurso {
        <<abstract>>
        tipo
        nome
        restrito
        ativo
        version
    }
    Recurso <|-- Sala
    Recurso <|-- Professor
    Recurso <|-- Material
    Sala : capacidade, bloco, andar, tipoSala
    Professor : matricula, email
    Material : patrimonio, tipoMaterial, estadoConservacao
    Professor "N" -- "N" Competencia
    Professor "0..1" -- "0..1" Usuario : login opcional
    Usuario "1" -- "N" Reserva : solicitante
    Reserva "1" -- "N" Ocupacao : um por recurso
    Bloqueio "1" -- "N" Ocupacao
    Ocupacao "N" -- "1" Recurso
    Material "1" -- "N" MovimentacaoMaterial
    Reserva "1" -- "N" MovimentacaoMaterial
    class RegistroAuditoria {
        entidade, entidadeId, acao
        estadoAnterior, estadoNovo
        motivo, usuario, ocorridoEm
    }
    class ConflitoEvitado {
        recurso, solicitante
        inicio, fim, origem
    }
```

| Decisão | Por quê |
|---|---|
| Sala, Professor e Material herdam de `Recurso` (JOINED) | Uma única coluna `recurso_id` e **uma única constraint** para os três tipos (a RC02 cita sala, material e professor). |
| Tabela `ocupacao` (recurso, período, bloqueante), alimentada por Reserva **ou** Bloqueio | RC02, RC03 e RC04 viram a mesma regra no banco. Uma reserva pode juntar sala, professor e materiais e é tudo ou nada. |
| Intervalo semiaberto `[inicio, fim)` | 08h–10h e 10h–12h **não** conflitam: intervalos que só se tocam são permitidos. |
| `PeriodoReserva` (value object) | Valida a RC01 no construtor e concentra `sobrepoe()`. É o alvo do TDD. |
| Única forma de mudar o estado: `Reserva.transitar(novo, usuario, motivo)` | Valida a RC05 e gera o `RegistroAuditoria` (RC08) no mesmo lugar. Não há `setStatus` público. |
| Datas em `Instant` / `TIMESTAMPTZ` (UTC) | Instante absoluto, sem ambiguidade de fuso. A API recebe ISO-8601 com offset e a interface exibe no horário de São Paulo. |
| Auditoria e reservas protegidas por trigger | Somente inserção na auditoria (V1); proibido DELETE em reservas (V2, RC07). Vale até contra SQL manual. |

### Máquina de estados (RC05)

```mermaid
stateDiagram-v2
    [*] --> SOLICITADA
    SOLICITADA --> APROVADA : RESPONSAVEL (ou sistema, se nada for restrito)
    SOLICITADA --> REJEITADA : RESPONSAVEL, motivo obrigatório
    SOLICITADA --> CANCELADA : dono ou ADMINISTRADOR
    APROVADA --> CANCELADA : dono ou ADMINISTRADOR, antes do início
    APROVADA --> EM_USO : RESPONSAVEL
    APROVADA --> NAO_COMPARECEU : RESPONSAVEL, após o início
    EM_USO --> CONCLUIDA : RESPONSAVEL
    CONCLUIDA --> [*]
    REJEITADA --> [*]
    CANCELADA --> [*]
    NAO_COMPARECEU --> [*]
```

São 7 estados, ou seja, 49 pares possíveis; só 7 são válidos. O teste parametrizado cobre os 49.
Ocupação **bloqueante** em SOLICITADA, APROVADA, EM_USO e CONCLUIDA; ao ir para REJEITADA,
CANCELADA ou NAO_COMPARECEU, `bloqueante = false` e o horário fica livre sem apagar nada.

## 3. Sobreposição e dupla reserva (RC02, RC03, RC04)

**O problema:** validar só na aplicação não basta. As transações T1 e T2 fazem o mesmo `SELECT`,
as duas veem o horário livre e as duas fazem `INSERT` (corrida *check-then-insert*).

| | `EXCLUDE USING gist` | `SELECT … FOR UPDATE` | `@Version` |
|---|---|---|---|
| Impede dois INSERTs simultâneos? | **Sim**: a 2ª transação espera a 1ª e falha com `23P01` | Só travando uma linha "pai" (o recurso); reserva inexistente não pode ser travada | **Não**: dois INSERTs não compartilham linha versionada |
| Onde fica a regra | No schema: vale para qualquer código, SQL manual ou várias instâncias | No código: todo caminho precisa lembrar de travar | No código |
| Custos | Só PostgreSQL; traduzir `23P01` em 409; índice GiST | Contenção; deadlock em reserva multi-recurso | Nova tentativa; falso conflito |
| Serve bem para | Garantir a regra "sem sobreposição" | Ler e modificar uma linha existente | Aprovar × cancelar a mesma reserva |

`SERIALIZABLE` também resolveria, mas exige nova tentativa após qualquer falha de serialização,
inclusive falsos positivos. Descartado.

**Escolha: defesa em camadas** (revisada na Etapa 3; ver 3.1).

1. **Lock pessimista no recurso:** `SELECT … FOR NO KEY UPDATE` na linha de `recurso` **antes** da
   pré-checagem, por uma consulta nativa única (`RecursoRepository.travar`). **Reservas e bloqueios
   travam a mesma linha**, então reserva × reserva e reserva × manutenção simultâneas são
   serializadas (cenários RC03 e MN-09).
2. **Aplicação:** valida a RC01 e consulta conflitos antes de gravar. Gera uma mensagem útil e
   registra o conflito evitado com origem `APLICACAO`. Com o lock, a consulta é confiável também
   sob concorrência.
3. **Banco:** `EXCLUDE USING gist (recurso_id WITH =, periodo WITH &&) WHERE (bloqueante)` é a
   **garantia final**, que vale até para código que esqueça o lock. `23P01` vira
   `ConflitoHorarioException` (HTTP 409) e o conflito é registrado com origem `BANCO`. Usar
   `saveAndFlush` para o erro surgir dentro do caso de uso, e não no commit.
4. **`@Version`** em Reserva e Recurso: transições concorrentes recebem 409 "edição concorrente".

### 3.1 O que o teste de concorrência ensinou

A primeira versão usava só a pré-checagem e a constraint. O teste RC03 (20 requisições
simultâneas, em 10 rodadas) mostrou:

- **A unicidade nunca foi violada:** sempre exatamente 1 reserva gravada.
- **Mas houve deadlock** em 3 de 10 rodadas (`40P01 deadlock detected … while checking exclusion
  constraint`), e o usuário recebia **500**. A constraint de exclusão verifica conflitos *depois*
  de inserir no índice; duas inserções conflitantes simultâneas esperam uma pela outra, e o
  PostgreSQL aborta uma delas. (`UNIQUE` não tem esse problema; a constraint de exclusão tem.)

Correção: travar a linha do recurso antes da pré-checagem. Depois dela, foram 30 de 30 rodadas
sem deadlock, e o teste ficou cerca de 25× mais rápido. **Conclusão:** lock pessimista e constraint
não são alternativas, e sim camadas complementares. O lock garante o comportamento correto
(409 com mensagem certa, sem deadlock) e a constraint garante a regra mesmo se o lock for
esquecido. Para reservas com vários recursos, travar sempre em **ordem crescente de id**, para
evitar deadlock entre recursos. Os commits `d485436` (red) e `de862b9` (green) registram essa
mudança.

**Por que funciona:** como `UNIQUE`, a constraint de exclusão também enxerga linhas ainda não
confirmadas de outra transação. T2 encontra a linha conflitante de T1 e **espera**. Se T1 confirmar,
T2 recebe `23P01`; se T1 desfizer, T2 segue. Exatamente uma reserva é aceita, já em `READ COMMITTED`.
A extensão `btree_gist` permite combinar `=` em `BIGINT` com `&&` em `tstzrange` no mesmo índice GiST.

## 4. Regras de negócio esclarecidas (decididas em 14/11/2026)

| # | Ambiguidade | Decisão |
|---|---|---|
| 1 | Recurso **não** restrito precisa de aprovação? | Não: o sistema aprova automaticamente (SOLICITADA → APROVADA), com auditoria. |
| 2 | Reserva SOLICITADA segura o horário? | Sim (ocupação bloqueante), para evitar disputa pela mesma aprovação. |
| 3 | Manutenção sobre reservas existentes | O sistema **recusa** e lista as reservas em conflito; o administrador decide o que cancelar. |
| 4 | "RESPONSAVEL valida a alocação de docentes" | Todo Professor é cadastrado como recurso **restrito**: reaproveita o fluxo de aprovação (RF07). |
| 5 | EM_USO e NAO_COMPARECEU | Registrados **manualmente** pelo RESPONSAVEL. Sem agendador automático. |
| 6 | RC07 "não podem ser apagadas" | Nenhuma reserva é apagada fisicamente (trigger da V2). Reservas EM_USO ou posteriores não podem ser canceladas nem alteradas. |
| 7 | Material unitário ou por quantidade? | Unitário (por patrimônio), para que a mesma constraint de exclusão valha. |
| 8 | Perfis por usuário | Um perfil por usuário. |
| 9 | Integração externa (RF11) | `POST /notificacoes` genérico, enviado **após o commit**. Uma falha gera log e não desfaz a reserva. WireMock nos testes. |

### Decisões da fatia de aprovação e estados (Fase 2)

Perguntas registradas em [docs/testes/bdd-aprovacao-bloqueio.md](testes/bdd-aprovacao-bloqueio.md#perguntas-em-aberto).

| # | Pergunta | Decisão |
|---|---|---|
| Q2 | Quem cria reservas? | Só o SOLICITANTE (os outros perfis recebem 403). A proibição de autoaprovação continua no domínio como defesa extra. |
| Q5 | Quando registrar NAO_COMPARECEU? | A partir do horário de início, sem tolerância fixa. |
| Q7 | Até quando cancelar uma reserva APROVADA? | Só **antes do início**. Depois do início sem uso, o RESPONSAVEL registra NAO_COMPARECEU. |
| Q8 | Aprovar depois do início? | Não: aprovar só antes do início. Uma solicitação ainda pendente pode ser rejeitada ou cancelada a qualquer momento. |
| Q4, Q6 | Início de uso antecipado? Conclusão antes do fim? | **Em aberto.** Por ora valem só as transições da máquina de estados, sem regra de horário. |

### Decisões da fatia de manutenção e bloqueios (Fase 2)

| # | Pergunta | Decisão |
|---|---|---|
| Q1 | Reserva que termina exatamente no início de uma manutenção (ou começa no fim) é aceita? | **Sim.** Mesma regra semiaberta `[início, fim)` das reservas entre si; nenhuma mudança no schema. |
| Q3 | Granularidade dos horários? | **Minutos cheios.** Segundos ou frações ≠ 0 são recusados com 400 (`@MinutoCheio` na entrada da API). |
| Q9 | Bloqueio ADMINISTRATIVO segue as regras da manutenção? | **Sim.** O tipo só muda a mensagem ("em manutenção" × "bloqueada") e os relatórios. |
| Q10 | Manutenção pode ser encerrada antes do fim? | **Sim, pelo ADMINISTRADOR**, com auditoria. Antes de começar, o bloqueio é desfeito; durante, termina no **próximo minuto cheio** (coerente com a Q3); depois do fim, recusado. |
| D3 | Bloqueio sobre reservas ativas | Recusado com 409 e a lista `reservasEmConflito` no ProblemDetail; o ADMINISTRADOR decide o que cancelar. |

### Decisões da fatia de login e usuários (Fase 2)

| Tema | Decisão | Por quê |
|---|---|---|
| Primeiro administrador | Criado na inicialização com `ADMIN_EMAIL`/`ADMIN_SENHA` do `.env`, se não houver nenhum ADMINISTRADOR ativo. Senha fraca ou e-mail em uso **impedem a subida**. | Nenhuma senha no código (SonarCloud S6437); falhar no deploy é melhor que subir sem administrador. |
| Política de senha | Mínimo 8 caracteres, com letra e número. Regra única em `PoliticaSenha` (domínio), usada pela API (`@SenhaForte`) e pela inicialização. | Fácil de explicar e testar nas fronteiras (7 × 8 caracteres). |
| Força bruta | 5 senhas erradas seguidas bloqueiam por 15 min (HTTP 429). Contador e desbloqueio gravados no usuário (migration V3). | Vale com várias instâncias e sobrevive a reinícios. |
| Mensagem de falha | **Sempre** "E-mail ou senha inválidos." (401), para e-mail inexistente, senha errada ou usuário inativo. | Não revelar quais e-mails existem. |
| Tempo de resposta | Sem usuário, a senha é comparada com um hash fictício. | O BCrypt é lento de propósito; sem isso, a resposta mais rápida denunciaria e-mails inexistentes (*timing attack*). |
| Falha gravada | `@Transactional(noRollbackFor = …)` no login. | Sem isso, a exceção desfaria o incremento do contador e o bloqueio nunca aconteceria. |
| Token | JWT HS256, `sub` = e-mail, claim `perfil`, validade `app.jwt.expiracao` (2 h). Emitido por uma porta (`EmissorDeToken`) implementada na infraestrutura. | O caso de uso não conhece o formato do token. |
| Gestão de usuários | Só ADMINISTRADOR cadastra, lista, desativa e redefine senha; ninguém desativa a si mesmo; e-mail normalizado e único (409). Cada mudança é auditada sem guardar senha. | Evita ficar sem administrador; RF10. |
| Troca da própria senha | Qualquer usuário logado, informando a senha atual (422 se não conferir). | Quem roubou só o token não troca a senha. |

### Decisões da fatia de cadastros e pesquisa (Fase 2)

Tomadas pela equipe técnica (sem regra no enunciado) e registradas aqui para a prova oral.

| Tema | Decisão | Por quê |
|---|---|---|
| Quem cadastra | Só o ADMINISTRADOR cadastra, altera, desativa e reativa. Qualquer usuário logado consulta e pesquisa. | O mesmo perfil que já gerencia usuários e bloqueios. |
| Sem exclusão | Recurso não é apagado, é **desativado**: some da pesquisa e recusa reservas novas (422, "está desativada"). Reativação permitida. | Reservas antigas apontam para o recurso e não podem ser apagadas (RC07); o histórico fica íntegro. |
| Desativar com reservas | Recusado (422) enquanto houver reserva SOLICITADA, APROVADA ou EM_USO que ainda não terminou; a mensagem diz quantas. | Desativar em silêncio deixaria quem reservou sem o recurso e sem aviso. Cancelar automaticamente seria uma ação grande demais para um efeito colateral. |
| Concorrência na desativação | Trava a linha do recurso (`RecursoRepository.travar`), como reservas e bloqueios. | Uma reserva simultânea espera e encontra o recurso inativo, ou termina antes e é contada. |
| Duplicidade | Nome de sala único sem diferenciar maiúsculas (índice parcial da **V4**); matrícula e patrimônio únicos (V1). Nomes de professor e de material podem repetir. | Duas "Sala 101" confundem quem reserva; dois projetores iguais são normais. A aplicação confere antes (mensagem clara, 409) e o índice garante sob concorrência. |
| Competências | Texto livre, normalizado (espaços) e comparado sem maiúsculas; reaproveitada entre professores, criada na hora se não existir. | Evita "Java" e "java" como competências diferentes sem exigir um cadastro separado de competências. |
| Restrito | Informado explicitamente em todo cadastro (`restrito` obrigatório). Alterar não muda reservas já feitas. | Evita um padrão escondido ("professor é restrito?") e efeitos retroativos. |
| Alteração concorrente | `@Version` em `Recurso` (409 "Edição concorrente"). | Duas alterações simultâneas do mesmo cadastro não se sobrescrevem em silêncio. |
| Localização | Bloco + andar da sala (andar pode ser negativo: subsolo). Professores e materiais não têm localização. | É o que o schema da V1 já modelava. |
| Disponibilidade | `inicio` e `fim` juntos (400 caso contrário); livre = nenhuma ocupação bloqueante sobreposta, pela **mesma regra** da constraint (encostar não conflita). Material DANIFICADO não é oferecido. | Pesquisa e reserva não podem discordar sobre o que é "livre". |
| Formato da pesquisa | `GET /api/recursos` com filtros na query string, combinados com "e"; página própria (`itens`, `pagina`, `tamanho`, `totalItens`, `totalPaginas`), até 100 itens, ordem por nome e id. | O JSON do `Page` do Spring não é estável entre versões; o id desempata nomes iguais entre páginas. |
| Como a consulta é montada | JPA Criteria (`PesquisaDeRecursos`): cada filtro informado vira uma subconsulta "id está em…". | Parâmetros sempre ligados (sem SQL concatenado, sem injeção) e filtro de sala que nunca afeta professores. |
| Resposta polimórfica | JSON "plano": campos comuns e, no mesmo nível, só os do tipo (`@JsonUnwrapped`). | O cliente lê `capacidade` direto, sem `if` por tipo de objeto aninhado. |

**Dívida quitada:** a busca "usuário do token existe e está ativo, senão 403" estava copiada em
quatro casos de uso; agora é o componente `UsuarioDoToken` (commit de refactor sem mudança de
comportamento).

**Lição do ArchUnit:** na primeira versão, o critério de pesquisa ficou em `domain.repository` e o
DTO da web passou a depender dele. O `ArquiteturaTest` reprovou o build antes do commit; o
critério foi movido para `domain.pesquisa`.

Ordem das verificações em toda transição: **quem pode** (perfil ou dono) → **transição permitida**
→ **regra de horário** → aplica (muda o estado, libera o horário se for o caso e registra a
mudança para a auditoria). A verificação de permissão vem primeiro para não revelar o estado da
reserva a quem não pode agir sobre ela.

## 5. Riscos

| Risco | Tipo | Mitigação |
|---|---|---|
| Prazo (semana 46) | Projeto | Priorizar as RC e a RTM; confirmar a data com o professor |
| Código gerado com IA e prova oral | Processo | Cada integrante é dono de uma fatia e revisa os PRs dos outros |
| `23P01` escapar como 500 | Técnico | `saveAndFlush` + teste de API exigindo 409 |
| **Deadlock `40P01`** entre inserções conflitantes (ocorreu na Etapa 3) | Técnico | Lock do recurso antes da pré-checagem; teste de concorrência com 10 rodadas |
| Teste de concorrência instável | Técnico | `CountDownLatch` como portão de largada; N ≥ 10; verificar 1 resposta 201, N−1 respostas 409 e nenhum 500; teste repetido |
| `inicio`/`fim` de `reserva` e `ocupacao` dessincronizados | Técnico | Atualizar tudo na mesma transação + teste de integração |
| Testes dependem de Docker (Testcontainers) | Técnico | Docker Desktop em todas as máquinas; o runner do CI já tem Docker |
| Gate de cobertura travar PRs | Técnico | Testar junto com cada funcionalidade |
| Spring Boot 3.x no fim do suporte gratuito | Técnico | Manter a última versão 3.5.x; a disciplina exige 3.x |
| Falha na notificação desfazer a reserva | Técnico | Notificar após o commit |
| Reservas SOLICITADAS usadas para "guardar lugar" | Negócio | RESPONSAVEL pode rejeitar |
