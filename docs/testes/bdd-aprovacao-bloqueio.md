# Cenários BDD: aprovação, autoaprovação, manutenção e estados da reserva

Especificação por exemplos de quatro regras do sistema. Escrita **antes do código** (Etapa 3) e
**revisada em 14/11/2026**, quando as perguntas em aberto já tinham sido decididas e todos os
cenários tinham teste automatizado.

| Regra | Requisitos | Prefixo dos cenários |
|---|---|---|
| 1. Recurso restrito exige aprovação de um RESPONSAVEL | RF07, RC06 | `AP-` |
| 2. O solicitante não aprova a própria reserva | RC06 | `AU-` |
| 3. Recurso em manutenção não pode ser reservado | RF08, RC04 | `MN-` |
| 4. A reserva só muda de estado pelas transições permitidas | RC05, RC07, RC08 | `ES-` |

Cada cenário tem um identificador estável (ex.: `MN-01`). O teste automatizado que o implementa
repete esse identificador no `@DisplayName`, e a [RTM](../rtm.md) aponta para ele. Assim dá para
seguir o caminho requisito → cenário → teste → evidência.

## Fontes e limites

- **Fontes usadas:**
  - o enunciado do projeto (RF01–RF14, RC01–RC08);
  - a matriz [`docs/rtm.md`](../rtm.md);
  - as decisões em [`docs/arquitetura.md`](../arquitetura.md);
  - o código das reservas: `Reserva`, `StatusReserva`, `PeriodoReserva`, `Bloqueio`, `AgendaDeRecursos` e a validação `@MinutoCheio`.
- **Não há PRD.** O arquivo `docs/prd.md` não existe no repositório. Onde um PRD decidiria uma
  fronteira, valem o enunciado e as decisões registradas da equipe. O que nenhuma fonte decide fica
  **EM ABERTO**, com uma pergunta na seção [Perguntas em aberto](#perguntas-em-aberto). Nenhuma
  política foi inventada para fechar uma lacuna.
- **Comportamento atual ≠ decisão de negócio.** Quando o código já faz algo numa fronteira em aberto,
  o documento registra o que ele faz hoje. Isso não transforma o comportamento em regra.

## Convenções

- **Horários** em ISO-8601 com fuso de Brasília (`-03:00`), em **minutos cheios** ([Q3](#q3)).
  Todos os exemplos são em 01/12/2026.
- **Intervalos semiabertos `[início, fim)`:** o instante final não pertence ao período. Vale para
  reserva × reserva (enunciado) e para reserva × manutenção ([Q1](#q1)).
- **"Agora"** é um relógio fixo nos testes de unidade (`Clock.fixed`). Valor padrão:
  `2026-12-01T07:00:00-03:00`. Os testes de API usam datas relativas a hoje.
- **Respostas de erro** em `ProblemDetail` (RFC 9457), em português, conforme o catálogo `TipoProblema`:

| Situação | HTTP | `title` |
|---|---|---|
| Campo inválido, ausente ou com segundos | 400 | Dados inválidos |
| Token inválido ou adulterado | 401 | Não autenticado |
| Perfil sem permissão ou ação sobre reserva de outra pessoa | 403 | Acesso negado |
| Horário ocupado por reserva ou manutenção | 409 | Conflito de horário |
| Duas pessoas alteraram a mesma reserva ao mesmo tempo | 409 | Edição concorrente |
| Transição de estado ou regra de horário não permitida | 422 | Regra de negócio violada |

- **Auditoria (RC08):** "gera auditoria" significa **um** registro com entidade `RESERVA`, o id da
  reserva, quem fez, quando, estado anterior, estado novo e motivo. Uma operação recusada **não**
  gera registro de transição.

## Dados comuns a todos os cenários

| Usuário | Perfil |
|---|---|
| ana@senac.br | SOLICITANTE |
| bruno@senac.br | SOLICITANTE |
| rita@senac.br | RESPONSAVEL |
| raul@senac.br | RESPONSAVEL |
| adm@senac.br | ADMINISTRADOR |

| Recurso | Tipo | Restrito? |
|---|---|---|
| Sala 101 | SALA | não |
| Sala 102 | SALA | não |
| Laboratório 3 | SALA | **sim** |
| Prof. Carlos | PROFESSOR | **sim**: todo professor é restrito (D4, [Q13](#q13)) |
| Projetor PAT-0012 | MATERIAL | não |

---

## Regra 1: recurso restrito exige aprovação de um RESPONSAVEL

```gherkin
# language: pt
Funcionalidade: Aprovação obrigatória para recursos restritos (RF07, RC06)

  Contexto:
    Dado os usuários e recursos da seção "Dados comuns"
    E que agora é "2026-12-01T07:00:00-03:00"

  Cenário: AP-01 Reserva só com recursos não restritos é aprovada automaticamente (D1)
    Dado que "ana@senac.br" está autenticada
    Quando ela solicita a "Sala 101" de "08:00" a "10:00"
    Então a resposta é 201
    E a reserva fica no estado "APROVADA", sem aprovador humano
    E são gerados 2 registros de auditoria, nesta ordem:
      | estado anterior | estado novo | autor             | motivo                                           |
      |                 | SOLICITADA  | ana@senac.br      |                                                  |
      | SOLICITADA      | APROVADA    | sistema           | Nenhum recurso é restrito: aprovação automática. |

  Cenário: AP-02 Reserva com recurso restrito fica aguardando e já segura o horário (D2)
    Quando "ana@senac.br" solicita o "Laboratório 3" de "08:00" a "10:00"
    Então a reserva fica no estado "SOLICITADA"
    E o horário do "Laboratório 3" fica ocupado
    Quando "bruno@senac.br" solicita o "Laboratório 3" de "09:00" a "11:00"
    Então a resposta é 409 com título "Conflito de horário"

  Cenário: AP-03 Basta um recurso restrito para a reserva inteira aguardar aprovação
    Quando "ana@senac.br" solicita, numa mesma reserva, a "Sala 101" e o "Prof. Carlos" de "08:00" a "10:00"
    Então a reserva fica no estado "SOLICITADA"
    E nem a "Sala 101" nem o "Prof. Carlos" ficam livres de "08:00" a "10:00"

  Cenário: AP-04 RESPONSAVEL aprova reserva de recurso restrito
    Dado que "ana@senac.br" solicitou o "Laboratório 3" de "08:00" a "10:00"
    Quando "rita@senac.br" aprova a reserva
    Então a resposta é 200
    E a reserva fica no estado "APROVADA" com aprovador "rita@senac.br"
    E é gerada auditoria de "SOLICITADA" para "APROVADA" com autor "rita@senac.br"

  Esquema do Cenário: AP-05 Somente o perfil RESPONSAVEL pode aprovar
    Dado que "ana@senac.br" solicitou o "Laboratório 3" de "08:00" a "10:00"
    Quando "<usuario>" tenta aprovar a reserva
    Então a resposta é 403 com título "Acesso negado"
    E a reserva continua "SOLICITADA"
    E nenhuma auditoria de transição é gerada

    Exemplos:
      | usuario        | perfil        |
      | bruno@senac.br | SOLICITANTE   |
      | adm@senac.br   | ADMINISTRADOR |

  Cenário: AP-06 RESPONSAVEL rejeita com motivo e o horário é liberado
    Dado que "ana@senac.br" solicitou o "Laboratório 3" de "08:00" a "10:00"
    Quando "rita@senac.br" rejeita a reserva com o motivo "Laboratório reservado para prova"
    Então a reserva fica no estado "REJEITADA"
    E é gerada auditoria de "SOLICITADA" para "REJEITADA" com o motivo informado
    Quando "bruno@senac.br" solicita o "Laboratório 3" de "08:00" a "10:00"
    Então a resposta é 201

  Esquema do Cenário: AP-07 Rejeição exige motivo
    Dado que "ana@senac.br" solicitou o "Laboratório 3" de "08:00" a "10:00"
    Quando "rita@senac.br" rejeita a reserva com o motivo "<motivo>"
    Então a resposta é 400 com título "Dados inválidos" apontando o campo "motivo"
    E a reserva continua "SOLICITADA"

    Exemplos:
      | motivo   |
      |          |
      | "   "    |

  # Q8 decidida: aprovar só antes do início.
  Esquema do Cenário: AP-08 Aprovação só antes do horário de início
    Dado que "ana@senac.br" solicitou o "Laboratório 3" de "08:00" a "10:00"
    E que agora é "<agora>"
    Quando "rita@senac.br" aprova a reserva
    Então o resultado é <resultado>

    Exemplos:
      | agora    | resultado                                                                    |
      | 07:59:59 | aprovada                                                                     |
      | 08:00:00 | 422 "A reserva só pode ser aprovada antes do horário de início."            |
      | 09:00:00 | 422 (a solicitação pendente ainda pode ser rejeitada ou cancelada)           |
```

---

## Regra 2: o solicitante não aprova a própria reserva

A regra tem duas leituras, e as duas valem:

- **(a) Pelo perfil:** quem tem perfil SOLICITANTE nunca aprova nem rejeita (RC06).
- **(b) Pela pessoa:** ninguém decide sobre a reserva que ele mesmo pediu (segregação de funções).

Como só o SOLICITANTE cria reservas ([Q2](#q2)), a leitura (b) não é alcançável pela API hoje. Ela
continua no domínio como defesa em profundidade (AU-04), para o caso de essa decisão mudar.

```gherkin
# language: pt
Funcionalidade: Solicitante não aprova a própria reserva (RC06)

  Contexto:
    Dado os usuários e recursos da seção "Dados comuns"
    E que agora é "2026-12-01T07:00:00-03:00"
    E que "ana@senac.br" solicitou o "Laboratório 3" de "08:00" a "10:00"

  Esquema do Cenário: AU-01 A dona da reserva, com perfil SOLICITANTE, não decide sobre ela
    Quando "ana@senac.br" tenta <acao> a própria reserva
    Então a resposta é 403 com título "Acesso negado"
    E a reserva continua "SOLICITADA" e sem aprovador
    E nenhuma auditoria de transição é gerada

    Exemplos:
      | acao     |
      | aprovar  |
      | rejeitar |

  Cenário: AU-02 Token adulterado para o perfil RESPONSAVEL é rejeitado
    Dado o token de "ana@senac.br" com o perfil trocado para "RESPONSAVEL", sem a assinatura correta
    Quando a aprovação da reserva é enviada com esse token
    Então a resposta é 401 com título "Não autenticado"
    E a reserva continua "SOLICITADA"

  Esquema do Cenário: AU-03 Estado e aprovador não podem ser enviados pelo cliente (mass assignment)
    Quando "ana@senac.br" <operacao> com o campo extra <campo>
    Então a resposta é 400
    E nenhuma reserva é criada ou alterada

    Exemplos:
      | operacao                                  | campo                   |
      | solicita o "Laboratório 3"                | "status": "APROVADA"    |
      | solicita o "Laboratório 3"                | "aprovadorId": 3        |
      | altera o horário da própria reserva       | "status": "APROVADA"    |

  # Defesa em profundidade: pela API, o RESPONSAVEL nem cria reservas (Q2 → 403).
  Cenário: AU-04 Um RESPONSAVEL não aprova uma reserva solicitada por ele mesmo
    Dado uma reserva do "Laboratório 3" cujo solicitante é "rita@senac.br"
    Quando "rita@senac.br" tenta aprová-la
    Então é recusado com "Você não pode aprovar ou rejeitar uma reserva solicitada por você."
    Quando "raul@senac.br" a aprova
    Então a reserva fica no estado "APROVADA" com aprovador "raul@senac.br"

  Cenário: AU-05 RESPONSAVEL não cria reserva pela API (Q2)
    Quando "rita@senac.br" solicita o "Laboratório 3" de "14:00" a "16:00"
    Então a resposta é 403 com título "Acesso negado"
```

---

## Regra 3: recurso em manutenção não pode ser reservado

### Tabela de fronteiras

Manutenção da **Sala 101** de **10:00:00 a 12:00:00** em 01/12/2026. Cada linha é uma nova reserva
da Sala 101 pedida por ana@senac.br.

| # | Situação | Início | Fim | Resultado esperado | Fonte |
|---|---|---|---|---|---|
| F1 | Totalmente antes | 08:00:00 | 09:00:00 | **aceita** (201) | RC04: sem sobreposição |
| F2 | Termina 1 s antes do início | 08:00:00 | 09:59:59 | **recusada** (400, horário com segundos) | [Q3](#q3) |
| F3 | **Termina exatamente no início** da manutenção | 08:00:00 | 10:00:00 | **aceita** (201) | [Q1](#q1): só encostar não conflita |
| F4 | Cruza **1 minuto** no início | 08:00:00 | 10:01:00 | **recusada** (409) | RC04 |
| F4s | Cruza **1 segundo** no início | 08:00:00 | 10:00:01 | **recusada** (400, horário com segundos) | [Q3](#q3) |
| F5 | Dentro da manutenção | 10:30:00 | 11:30:00 | **recusada** (409) | RC04 |
| F6 | Idêntica à manutenção | 10:00:00 | 12:00:00 | **recusada** (409) | RC04 |
| F7 | Cobre a manutenção inteira | 09:00:00 | 13:00:00 | **recusada** (409) | RC04 |
| F8 | Cruza **1 minuto** no fim | 11:59:00 | 13:00:00 | **recusada** (409) | RC04 |
| F8s | Cruza **1 segundo** no fim | 11:59:59 | 13:00:00 | **recusada** (400, horário com segundos) | [Q3](#q3) |
| F9 | **Começa exatamente no fim** da manutenção | 12:00:00 | 14:00:00 | **aceita** (201) | [Q1](#q1) |
| F10 | Começa 1 s depois do fim | 12:00:01 | 14:00:00 | **recusada** (400, horário com segundos) | [Q3](#q3) |
| F11 | Totalmente depois | 13:00:00 | 14:00:00 | **aceita** (201) | RC04 |

> **Sobre "cruzar um segundo".** Como os horários são em minutos cheios ([Q3](#q3)), uma reserva que
> cruza a manutenção por 1 segundo é recusada com **400 na validação da entrada**, antes de chegar à
> checagem de conflito. O menor cruzamento que chega à regra RC04 é de **1 minuto** (F4 e F8), e esse
> é recusado com 409. As duas recusas são corretas, mas por motivos diferentes, e por isso os dois
> casos aparecem na tabela.

```gherkin
# language: pt
Funcionalidade: Recurso em manutenção não pode ser reservado (RF08, RC04)

  Contexto:
    Dado os usuários e recursos da seção "Dados comuns"
    E que agora é "2026-12-01T07:00:00-03:00"
    E que "adm@senac.br" registrou manutenção da "Sala 101" de "10:00" a "12:00" com motivo "Troca do projetor"

  Esquema do Cenário: MN-01 Fronteiras do período de manutenção
    Quando "ana@senac.br" solicita a "Sala 101" de "<inicio>" a "<fim>"
    Então a resposta é <status>

    Exemplos: aceitas (inclusive as que só encostam, Q1)
      | caso | inicio   | fim      | status |
      | F1   | 08:00:00 | 09:00:00 | 201    |
      | F3   | 08:00:00 | 10:00:00 | 201    |
      | F9   | 12:00:00 | 14:00:00 | 201    |
      | F11  | 13:00:00 | 14:00:00 | 201    |

    Exemplos: recusadas por sobreposição
      | caso | inicio   | fim      | status |
      | F4   | 08:00:00 | 10:01:00 | 409    |
      | F5   | 10:30:00 | 11:30:00 | 409    |
      | F6   | 10:00:00 | 12:00:00 | 409    |
      | F7   | 09:00:00 | 13:00:00 | 409    |
      | F8   | 11:59:00 | 13:00:00 | 409    |

    Exemplos: recusadas na entrada, por terem segundos (Q3)
      | caso | inicio   | fim      | status |
      | F2   | 08:00:00 | 09:59:59 | 400    |
      | F4s  | 08:00:00 | 10:00:01 | 400    |
      | F8s  | 11:59:59 | 13:00:00 | 400    |
      | F10  | 12:00:01 | 14:00:00 | 400    |

  Cenário: MN-02 Mensagem compreensível quando o recurso está em manutenção (RF13)
    Quando "ana@senac.br" solicita a "Sala 101" de "10:30" a "11:30"
    Então a resposta é 409 com título "Conflito de horário"
    E o detalhe informa que a "Sala 101" está em manutenção das "10:00" às "12:00"
    E o detalhe não contém SQL, nome de constraint nem nome de classe
    E é registrado um conflito evitado para a "Sala 101" (RF12)

  Cenário: MN-03 Manutenção de um recurso não afeta os outros
    Quando "ana@senac.br" solicita a "Sala 102" de "10:00" a "12:00"
    Então a resposta é 201

  Cenário: MN-04 Reserva com vários recursos é tudo ou nada
    Dado que "adm@senac.br" registrou manutenção do "Projetor PAT-0012" de "08:00" a "18:00"
    Quando "ana@senac.br" solicita, numa mesma reserva, a "Sala 102" e o "Projetor PAT-0012" de "14:00" a "16:00"
    Então a resposta é 409 e o detalhe cita o projetor pelo patrimônio
    E nenhuma reserva é criada
    E a "Sala 102" continua livre de "14:00" a "16:00"

  Cenário: MN-05 Alterar reserva para dentro da manutenção é recusado (RF04)
    Dado que "ana@senac.br" tem reserva aprovada da "Sala 101" de "13:00" a "14:00"
    Quando ela altera o horário para "11:00" a "12:00"
    Então a resposta é 409
    E a reserva mantém o horário de "13:00" a "14:00"

  Esquema do Cenário: MN-06 Manutenção sobre reserva ativa é recusada e lista os conflitos (D3)
    Dado que existe reserva da "Sala 102" de "14:00" a "16:00" no estado "<estado>"
    Quando "adm@senac.br" registra manutenção da "Sala 102" de "15:00" a "17:00"
    Então a resposta é 409
    E a resposta lista essa reserva em "reservasEmConflito"
    E nenhuma manutenção é registrada

    Exemplos:
      | estado     |
      | SOLICITADA |
      | APROVADA   |
      | EM_USO     |

  Esquema do Cenário: MN-07 Reservas que já liberaram o horário não impedem a manutenção
    Dado que existe reserva da "Sala 102" de "14:00" a "16:00" no estado "<estado>"
    Quando "adm@senac.br" registra manutenção da "Sala 102" de "15:00" a "17:00"
    Então a resposta é 201

    Exemplos:
      | estado         |
      | REJEITADA      |
      | CANCELADA      |
      | NAO_COMPARECEU |

  Esquema do Cenário: MN-08 Somente ADMINISTRADOR registra manutenção
    Quando "<usuario>" tenta registrar manutenção da "Sala 102" de "15:00" a "17:00"
    Então a resposta é 403

    Exemplos:
      | usuario        |
      | ana@senac.br   |
      | rita@senac.br  |

  Cenário: MN-09 Reserva e manutenção simultâneas no mesmo horário: só uma é gravada
    Dado que a "Sala 102" está livre de "15:00" a "17:00"
    Quando, no mesmo instante, "ana@senac.br" solicita a "Sala 102" de "15:00" a "16:00"
    E "adm@senac.br" registra manutenção da "Sala 102" de "15:00" a "17:00"
    Então exatamente uma das duas operações retorna 201 e a outra retorna 409
    E nenhuma resposta é 500

  # Q9 e Q10 decididas.
  Cenário: MN-10 Bloqueio administrativo segue as mesmas regras da manutenção (Q9)
    Dado que "adm@senac.br" registrou bloqueio ADMINISTRATIVO da "Sala 102" de "10:00" a "12:00"
    Quando "ana@senac.br" solicita a "Sala 102" de "10:30" a "11:30"
    Então a resposta é 409 e o detalhe diz que a sala "está bloqueada"

  Cenário: MN-11 Encerrar a manutenção antes do previsto libera o recurso (Q10)
    Dado que agora é "2026-12-01T10:30:20-03:00"
    Quando "adm@senac.br" encerra a manutenção da "Sala 101"
    Então a manutenção passa a terminar às "10:31" (próximo minuto cheio)
    E "ana@senac.br" consegue reservar a "Sala 101" de "10:31" a "12:00"
```

---

## Regra 4: a reserva só muda de estado pelas transições permitidas

Fluxo principal: SOLICITADA → APROVADA → EM_USO → CONCLUIDA. Estados alternativos: REJEITADA,
CANCELADA, NAO_COMPARECEU. (No código o estado é `CONCLUIDA`, sem acento.)

### Matriz completa (7 × 7 = 49 pares)

✅ = transição permitida · ❌ = recusada com 422, sem auditoria

| De \ Para | SOLICITADA | APROVADA | EM_USO | CONCLUIDA | REJEITADA | CANCELADA | NAO_COMPARECEU |
|---|---|---|---|---|---|---|---|
| **SOLICITADA** | ❌ | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ |
| **APROVADA** | ❌ ¹ | ❌ | ✅ | ❌ | ❌ | ✅ | ✅ |
| **EM_USO** | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| **CONCLUIDA** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **REJEITADA** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **CANCELADA** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **NAO_COMPARECEU** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

São 7 transições permitidas e 42 recusadas. A linha EM_USO → CANCELADA = ❌ é a RC07 aplicada à
máquina de estados: reserva iniciada não pode ser desfeita.

¹ **Exceção decidida ([Q11](#q11)):** nenhuma *ação de transição* leva APROVADA a
SOLICITADA. Mas a **alteração de horário** de uma reserva APROVADA que tem recurso restrito a devolve
a SOLICITADA, para ser aprovada de novo (ES-06). É o único caminho que volta um estado, e fica
auditado como `ALTERACAO`, fora das ações de transição da matriz.

```gherkin
# language: pt
Funcionalidade: Máquina de estados da reserva (RC05, RC07, RC08)

  Contexto:
    Dado os usuários e recursos da seção "Dados comuns"
    E que existe reserva do "Laboratório 3" de "08:00" a "10:00" solicitada por "ana@senac.br"

  Esquema do Cenário: ES-01 Transições permitidas geram auditoria
    Dado que a reserva está no estado "<de>"
    E que agora é "<agora>"
    Quando "<ator>" executa "<acao>" com motivo "<motivo>"
    Então a reserva fica no estado "<para>"
    E é gerado exatamente 1 registro de auditoria com: autor "<ator>", data "<agora>", anterior "<de>", novo "<para>" e motivo "<motivo>"
    E o horário fica <horario>

    Exemplos:
      | de         | acao                         | ator          | agora    | motivo               | para           | horario  |
      | SOLICITADA | aprovar                      | rita@senac.br | 07:00:00 |                      | APROVADA       | ocupado  |
      | SOLICITADA | rejeitar                     | rita@senac.br | 07:00:00 | sala em prova        | REJEITADA      | liberado |
      | SOLICITADA | cancelar                     | ana@senac.br  | 07:00:00 | aula remarcada       | CANCELADA      | liberado |
      | APROVADA   | cancelar                     | ana@senac.br  | 07:00:00 | aula remarcada       | CANCELADA      | liberado |
      | APROVADA   | cancelar                     | adm@senac.br  | 07:00:00 | evento institucional | CANCELADA      | liberado |
      | APROVADA   | registrar início de uso      | rita@senac.br | 08:00:00 |                      | EM_USO         | ocupado  |
      | APROVADA   | registrar não comparecimento | rita@senac.br | 08:00:00 | ninguém compareceu   | NAO_COMPARECEU | liberado |
      | EM_USO     | concluir                     | rita@senac.br | 10:00:00 |                      | CONCLUIDA      | ocupado  |

  # Amostra das 42 transições recusadas; o teste de unidade cobre a matriz inteira.
  Esquema do Cenário: ES-02 Transições não permitidas são recusadas
    Dado que a reserva está no estado "<de>"
    Quando um usuário com permissão tenta levá-la para "<para>"
    Então a resposta é 422 com título "Regra de negócio violada"
    E o detalhe diz, em português, que não é possível mudar de "<de>" para "<para>" e quais são as opções
    E a reserva continua "<de>"
    E nenhuma auditoria de transição é gerada

    Exemplos:
      | de             | para       | por que é um caso importante                         |
      | SOLICITADA     | EM_USO     | pular a aprovação                                    |
      | SOLICITADA     | CONCLUIDA  | pular aprovação e uso                                |
      | APROVADA       | APROVADA   | aprovar duas vezes                                   |
      | EM_USO         | CANCELADA  | RC07: reserva iniciada não pode ser desfeita         |
      | CONCLUIDA      | APROVADA   | reabrir reserva encerrada                            |
      | REJEITADA      | APROVADA   | reverter rejeição                                    |
      | CANCELADA      | SOLICITADA | "descancelar"                                        |
      | NAO_COMPARECEU | EM_USO     | chegada atrasada depois do registro de ausência      |

  Cenário: ES-03 Outro solicitante não cancela reserva alheia
    Dado que a reserva está no estado "APROVADA"
    Quando "bruno@senac.br" tenta cancelá-la
    Então a resposta é 403
    E a reserva continua "APROVADA"

  # Q7 decidida: reserva APROVADA só é cancelada antes do início.
  Esquema do Cenário: ES-04 Cancelamento de reserva aprovada, pelo relógio
    Dado que a reserva está no estado "APROVADA"
    E que agora é "<agora>"
    Quando "ana@senac.br" cancela a reserva
    Então o resultado é <resultado>

    Exemplos:
      | agora    | resultado                                                                          |
      | 07:59:59 | cancelada                                                                          |
      | 08:00:00 | 422 "Uma reserva aprovada só pode ser cancelada antes do horário de início."       |
      | 08:30:00 | 422 (mesmo sem início de uso registrado)                                           |

  Cenário: ES-05 Duas decisões simultâneas sobre a mesma reserva: só uma vence
    Dado que a reserva está no estado "SOLICITADA"
    Quando, no mesmo instante, "rita@senac.br" aprova e "ana@senac.br" cancela
    Então exatamente uma operação tem sucesso
    E a outra recebe 409 "Edição concorrente" ou 422 "Regra de negócio violada"
    E existe exatamente 1 registro de auditoria de transição
    E nenhuma resposta é 500

  # Q11 decidida: a aprovação valia para o horário antigo.
  Cenário: ES-06 Alterar o horário de reserva aprovada com recurso restrito exige nova aprovação
    Dado que a reserva está no estado "APROVADA", aprovada por "rita@senac.br"
    E que agora é "2026-12-01T07:00:00-03:00"
    Quando "ana@senac.br" altera o horário para "14:00" a "16:00"
    Então a reserva fica no estado "SOLICITADA", sem aprovador
    E é gerada auditoria "ALTERACAO" de "APROVADA" para "SOLICITADA" com "Horário alterado de 01/12/2026 08:00–10:00 para 01/12/2026 14:00–16:00."
    Quando "rita@senac.br" aprova de novo
    Então a reserva fica no estado "APROVADA"
```

### Fronteiras de horário das transições

Reserva aprovada de 08:00 a 10:00. As linhas **EM ABERTO** registram o comportamento atual do código,
que não é uma decisão de negócio.

| Ação | Agora | Resultado | Fonte |
|---|---|---|---|
| Aprovar (SOLICITADA) | 07:59:59 | permitido | [Q8](#q8) |
| Aprovar (SOLICITADA) | 08:00:00 | recusado (422) | [Q8](#q8) |
| Rejeitar ou cancelar uma SOLICITADA | depois das 08:00 | permitido | [Q8](#q8) |
| Cancelar (APROVADA) | 07:59:59 | permitido | [Q7](#q7) |
| Cancelar (APROVADA) | 08:00:00 ou depois, mesmo sem início de uso | recusado (422) | [Q7](#q7) |
| Registrar não comparecimento | 07:59:59 | recusado (422) | [Q5](#q5) |
| Registrar não comparecimento | 08:00:00 (exatamente no início) | permitido | [Q5](#q5) |
| Registrar não comparecimento | depois das 10:00 (após o fim) | **EM ABERTO** · hoje: permitido | [Q5](#q5) |
| Registrar início de uso | 07:45:00 (antes do início) | **EM ABERTO** · hoje: permitido | [Q4](#q4) |
| Concluir | 09:00:00 (antes do fim) | **EM ABERTO** · hoje: permitido, e o horário continua ocupado até as 10:00 | [Q6](#q6) |
| Alterar horário | 07:59 | permitido | decisão da fatia de alteração |
| Alterar horário | 08:00:00 | recusado (422) | decisão da fatia de alteração |
| Criar reserva | início às 08:00, pedida às 08:00 ou depois | recusado (422 "A reserva deve começar no futuro.") | [Q12](#q12) |
| Criar reserva | início às 08:00, pedida às 07:59 | permitido | [Q12](#q12) |

---

## Perguntas em aberto

As decisões são da equipe (ou do professor, como cliente). Respondida uma pergunta, mova os
exemplos afetados para as tabelas executáveis e registre a decisão em `docs/arquitetura.md`.

| # | Pergunta | Cenários afetados | Situação |
|---|---|---|---|
| <a id="q1"></a>Q1 | Reserva que **termina exatamente no início** de uma manutenção (ou começa exatamente no fim) é aceita, ou a manutenção precisa de margem? | MN-01 (F3, F9) | ✅ **Decidido:** aceita (semiaberto, como reserva × reserva) |
| <a id="q2"></a>Q2 | RESPONSAVEL e ADMINISTRADOR podem **criar** reservas? | AU-04, AU-05 | ✅ **Decidido:** só o SOLICITANTE cria (403 para os demais) |
| <a id="q3"></a>Q3 | Qual a **granularidade** dos horários? | MN-01 (F2, F4s, F8s, F10) | ✅ **Decidido:** minutos cheios; segundos → 400 |
| <a id="q4"></a>Q4 | O RESPONSAVEL pode registrar o **início de uso** antes do horário reservado? Com quanta antecedência? | ES-01, fronteiras | ⏳ **Em aberto.** Hoje: sem regra de horário |
| <a id="q5"></a>Q5 | O **não comparecimento** pode ser registrado a partir do instante exato do início ou só após uma tolerância? E **depois do fim**? | ES-01, fronteiras | ✅ **Decidido:** a partir do início, sem tolerância. ⏳ Depois do fim: não decidido (hoje permitido) |
| <a id="q6"></a>Q6 | **Concluir antes do fim** é permitido? Se for, o tempo restante é liberado? | ES-01, fronteiras | ⏳ **Em aberto.** Hoje: permitido, e o horário continua ocupado até o fim |
| <a id="q7"></a>Q7 | O cancelamento é limitado pelo **relógio** (antes do início) ou só pelo **estado** (antes de EM_USO)? | ES-04 | ✅ **Decidido:** pelo relógio; APROVADA só é cancelada antes do início |
| <a id="q8"></a>Q8 | Uma SOLICITADA pode ser **aprovada depois do início**? | AP-08 | ✅ **Decidido:** aprovar só antes do início; a pendente pode ser rejeitada ou cancelada depois |
| <a id="q9"></a>Q9 | Bloqueio **ADMINISTRATIVO** segue as mesmas regras da manutenção? | MN-10 | ✅ **Decidido:** mesmas regras |
| <a id="q10"></a>Q10 | Manutenção pode ser **encerrada antes** do fim previsto? | MN-11 | ✅ **Decidido:** o ADMINISTRADOR encerra; o fim passa ao próximo minuto cheio |
| <a id="q11"></a>Q11 | Alterar o horário de uma reserva **APROVADA com recurso restrito** deve devolvê-la a SOLICITADA (nova aprovação), como faz hoje? A alternativa seria proibir a alteração e exigir cancelar e pedir de novo. | ES-06, nota ¹ da matriz | ✅ **Decidido:** mantém a volta a SOLICITADA, auditada como ALTERACAO. A aprovação valia para o horário antigo, e a RC06 exige aprovação para recurso restrito |
| <a id="q12"></a>Q12 | Uma reserva pode ser **criada com horário no passado**? (A alteração de horário já exige horário futuro.) | ES-01 (não comparecimento nos testes de API) | ✅ **Decidido:** não; a reserva precisa começar no futuro (422), como na alteração |
| <a id="q13"></a>Q13 | **Todo professor é restrito?** A decisão D4 em `docs/arquitetura.md` diz que sim, mas o cadastro aceitava professor não restrito (o campo `restrito` era informado livremente). | AP-03 | ✅ **Decidido:** vale a D4; o cadastro de professor não tem mais o campo `restrito` (o professor é sempre restrito) |

---

## Tipo de teste de cada cenário

**Teste de unidade:** JUnit 5 sem Spring e sem banco, rápido. Testa as regras puras do domínio
(`StatusReserva`, `Reserva`, `PeriodoReserva`, `Bloqueio`) com `@ParameterizedTest` e relógio fixo.

**Teste de API:** caixa-preta, com requisição HTTP e JWT reais, banco PostgreSQL real
(Testcontainers) e constraint e triggers ativos. Verifica status, `ProblemDetail`, autorização
e o que foi gravado.

Autorização (403, 401) só tem valor medida na API, porque quem nega é o Spring Security. Já as regras
puras são medidas na unidade, onde é barato testar todas as combinações.

| Cenário | Unidade | API | Onde está hoje | O que cada nível prova |
|---|:-:|:-:|---|---|
| AP-01 aprovação automática | ✅ | ✅ | `ReservaTest`, `CriarReservaSalaIT` | Unidade: estado inicial a partir dos recursos. API: os 2 registros de auditoria. |
| AP-02 restrito fica SOLICITADA e segura o horário | ✅ | ✅ | `ReservaTest`, `CriarReservaSalaIT` | Unidade: o estado. API: ocupação bloqueante gravada. |
| AP-03 um restrito basta | ✅ | ✅ | `ReservaTest`, `ReservaComVariosRecursosTest`, `ReservaComVariosRecursosIT` | Regra sobre a lista de recursos |
| AP-04 RESPONSAVEL aprova | ✅ | ✅ | `ReservaTest`, `TransicoesReservaIT` | Unidade: aprovador e auditoria. API: fluxo HTTP. |
| AP-05 perfis sem permissão | ✅ | ✅ | `ReservaTest`, `TransicoesReservaIT` | Unidade: o domínio também recusa. API: 403. |
| AP-06 rejeição libera horário | ✅ | ✅ | `ReservaTest`, `TransicoesReservaIT` | API: horário liberado no banco |
| AP-07 rejeição sem motivo | ✅ | ✅ | `ReservaTest`, `TransicoesReservaIT` | API: 400 apontando o campo |
| AP-08 aprovar só antes do início | ✅ | | `ReservaTest` (Q8, fronteira de 1 s) | Regra de tempo com `Clock.fixed` |
| AU-01 dona não decide | ✅ | ✅ | `ReservaTest`, `TransicoesReservaIT` | API: 403 |
| AU-02 token adulterado | | ✅ | `TransicoesReservaIT` | A assinatura do JWT protege o perfil (401) |
| AU-03 mass assignment | | ✅ | `CriarReservaSalaIT` (`status`, `aprovadorId`), `AlteracaoHorarioIT` (`status`) | `fail-on-unknown-properties` + DTO, sem gravar nada |
| AU-04 RESPONSAVEL não se autoaprova | ✅ | | `ReservaTest` | Defesa em profundidade (inalcançável pela API após Q2) |
| AU-05 RESPONSAVEL não cria reserva | | ✅ | `CriarReservaSalaIT` | Autorização (403) |
| MN-01 fronteiras | ✅ | ✅ | `PeriodoReservaTest` (RC02), `BloqueiosIT`, `CriarReservaSalaIT` e `MinutoCheioValidatorTest` (Q3) | Unidade: `sobrepoe` nas fronteiras. API: as linhas de minuto contra a constraint real; as de segundo pela validação. |
| MN-02 mensagem compreensível | | ✅ | `BloqueiosIT`, `ConflitosEvitadosIT` | ProblemDetail sem detalhe interno + conflito evitado gravado |
| MN-03 outro recurso livre | | ✅ | `BloqueiosIT` | Constraint por recurso |
| MN-04 tudo ou nada | | ✅ | `ReservaComVariosRecursosIT` | Rollback no banco real |
| MN-05 alteração para dentro da manutenção | | ✅ | `AlteracaoHorarioIT` | Recusa sem mudar o horário gravado |
| MN-06 manutenção sobre reserva ativa | | ✅ | `BloqueiosIT` (SOLICITADA, APROVADA, EM_USO) | Constraint + listagem dos conflitos |
| MN-07 reservas inativas não impedem | | ✅ | `BloqueiosIT` (CANCELADA, REJEITADA, NAO_COMPARECEU) | Ocupações não bloqueantes |
| MN-08 só ADMINISTRADOR | ✅ | ✅ | `BloqueioTest`, `BloqueiosIT` | API: 403 |
| MN-09 reserva × manutenção simultâneas | | ✅ | `BloqueiosIT` | Concorrência real (5 rodadas) |
| MN-10 bloqueio administrativo | ✅ | ✅ | `BloqueioTest`, `BloqueiosIT` (Q9) | Mesmas regras da manutenção |
| MN-11 encerramento antecipado | ✅ | ✅ | `BloqueioTest`, `BloqueiosIT` (Q10) | Próximo minuto cheio; recurso liberado |
| ES-01 transições permitidas | ✅ | ✅ | `StatusReservaTest`, `ReservaTest`, `TransicoesReservaIT` | Unidade: as 7 transições e a auditoria. API: fluxo principal ponta a ponta. |
| ES-02 transições recusadas | ✅ | ✅ | `StatusReservaTest` (**49 pares**), `TransicoesReservaIT` | API: o 422 em ProblemDetail |
| ES-03 reserva alheia | ✅ | ✅ | `ReservaTest`, `TransicoesReservaIT` | API: 403 |
| ES-04 cancelamento pelo relógio | ✅ | | `ReservaTest` (Q7) | Regra de tempo com `Clock.fixed` |
| ES-05 decisões simultâneas | | ✅ | `TransicoesReservaIT` | `@Version` + concorrência real |
| ES-06 alteração exige nova aprovação | ✅ | ✅ | `AlteracaoReservaTest`, `AlteracaoHorarioIT` | Volta a SOLICITADA e é reaprovada |

**Resumo:** 30 cenários, todos com teste automatizado e com todos os exemplos cobertos. **19 têm teste
de unidade e 27 têm teste de API; 16 têm os dois.**

### Por que não usar Cucumber

Os cenários ficam aqui, em Gherkin, como especificação. Os testes são JUnit 5 com
`@DisplayName("MN-01 …")`. Usar Cucumber exigiria mais uma dependência, uma camada de "glue code" e
outro executor, e o ganho principal dele, ler os testes como texto, já está neste documento. Com
JUnit, todos os testes entram no mesmo relatório do Surefire/Failsafe e na mesma cobertura do JaCoCo.
