# Cenários BDD: aprovação, autoaprovação, manutenção e estados da reserva

Especificação por exemplos, escrita **antes do código**, para quatro regras do sistema:

| Regra | Requisitos | Prefixo dos cenários |
|---|---|---|
| 1. Recurso restrito exige aprovação de um RESPONSAVEL | RF07, RC06 | `AP-` |
| 2. O solicitante não aprova a própria reserva | RC06 | `AU-` |
| 3. Recurso em manutenção não pode ser reservado | RF08, RC04 | `MN-` |
| 4. A reserva só muda de estado pelas transições permitidas | RC05, RC07, RC08 | `ES-` |

Cada cenário tem um identificador estável (ex.: `MN-01`). O teste automatizado que o implementa
repete esse identificador no `@DisplayName`, e a RTM aponta para ele. Assim dá para seguir o
caminho requisito → cenário → teste → evidência.

## Fontes e limites

- **Fontes usadas:** o enunciado do projeto (RF01–RF14, RC01–RC08) e as decisões aprovadas em
  [`docs/arquitetura.md`](../arquitetura.md), seção 4 (D1 a D9).
- **Fontes que não existiam quando os cenários foram escritos:** um PRD (`docs/prd.md`, ainda
  inexistente) e a RTM, hoje em [`RTM.md`](../../RTM.md). O código de reserva também não existia;
  havia só o schema (migrations V1 e V2).
- **Regra deste documento:** quando as fontes não decidem um caso, o resultado esperado fica
  **EM ABERTO** e aponta para uma pergunta na seção [Perguntas em aberto](#perguntas-em-aberto).
  Esses exemplos ficam fora das tabelas executáveis até a equipe decidir.

## Convenções

- **Horários** em ISO-8601 com fuso de Brasília (`-03:00`). Todos os exemplos são em 01/12/2026.
- **Intervalos semiabertos `[início, fim)`:** o instante final não pertence ao período. Isso está
  **decidido para reserva × reserva** (o enunciado manda: "fim de uma = início da outra não é
  conflito"). Para reserva × manutenção, ver [Q1](#q1).
- **"Agora"** é um relógio fixo nos testes (`Clock.fixed`). Valor padrão: `2026-12-01T07:00:00-03:00`.
- **Respostas de erro** em `ProblemDetail` (RFC 9457), em português, conforme o catálogo `TipoProblema`:

| Situação | HTTP | `title` |
|---|---|---|
| Campo inválido ou ausente | 400 | Dados inválidos |
| Perfil sem permissão ou ação sobre reserva de outra pessoa | 403 | Acesso negado |
| Horário ocupado por reserva ou manutenção | 409 | Conflito de horário |
| Duas pessoas alteraram a mesma reserva ao mesmo tempo | 409 | Edição concorrente |
| Transição de estado não permitida | 422 | Regra de negócio violada |

- **Auditoria (RC08):** "gera auditoria" significa **um** registro com entidade `RESERVA`, o id da
  reserva, quem fez, quando (o relógio fixo), estado anterior, estado novo e motivo. Uma operação
  recusada **não** gera registro de transição.

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
| Prof. Carlos | PROFESSOR | **sim** (todo professor é restrito, decisão D4) |
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
      | estado anterior | estado novo | autor             | motivo                     |
      |                 | SOLICITADA  | ana@senac.br      | criação                    |
      | SOLICITADA      | APROVADA    | sistema           | nenhum recurso é restrito  |

  Cenário: AP-02 Reserva com recurso restrito fica aguardando e já segura o horário (D2)
    Dado que "ana@senac.br" solicitou o "Laboratório 3" de "08:00" a "10:00"
    Então a reserva fica no estado "SOLICITADA"
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
```

Ver também [Q8](#q8): aprovação de reserva cujo horário já começou.

---

## Regra 2: o solicitante não aprova a própria reserva

A regra tem duas leituras, e as duas valem:

- **(a) Pelo perfil:** quem tem perfil SOLICITANTE nunca aprova nem rejeita (RC06).
- **(b) Pela pessoa:** ninguém decide sobre a reserva que ele mesmo pediu (segregação de funções).
  Só faz diferença se um RESPONSAVEL puder criar reservas ([Q2](#q2)).

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
    Dado o token de "ana@senac.br" com o perfil trocado para "RESPONSAVEL", sem nova assinatura
    Quando a aprovação da reserva é enviada com esse token
    Então a resposta é 401 com título "Não autenticado"
    E a reserva continua "SOLICITADA"

  Esquema do Cenário: AU-03 Estado e aprovador não podem ser enviados pelo cliente (mass assignment)
    Quando "ana@senac.br" <operacao> com o campo extra <campo>
    Então a resposta é 400 com título "Dados inválidos"
    E nenhuma reserva é criada ou alterada

    Exemplos:
      | operacao                                  | campo                   |
      | solicita a "Laboratório 3"                | "status": "APROVADA"    |
      | solicita a "Laboratório 3"                | "aprovadorId": 3        |
      | altera o horário da própria reserva       | "status": "APROVADA"    |

  # Condicionado à Q2: só é alcançável se um RESPONSAVEL puder criar reservas.
  Cenário: AU-04 Um RESPONSAVEL não aprova a reserva que ele mesmo solicitou
    Dado que "rita@senac.br" solicitou o "Laboratório 3" de "14:00" a "16:00"
    Quando "rita@senac.br" tenta aprovar essa reserva
    Então a resposta é 403 com o detalhe "Você não pode aprovar uma reserva solicitada por você."
    E a reserva continua "SOLICITADA"
    Quando "raul@senac.br" aprova essa reserva
    Então a reserva fica no estado "APROVADA" com aprovador "raul@senac.br"
```

---

## Regra 3: recurso em manutenção não pode ser reservado

### Tabela de fronteiras

Manutenção da **Sala 101** de **10:00:00 a 12:00:00** em 01/12/2026. Cada linha é uma nova reserva
da Sala 101 pedida por ana@senac.br.

| # | Situação | Início | Fim | Resultado esperado | Fonte |
|---|---|---|---|---|---|
| F1 | Totalmente antes | 08:00:00 | 09:00:00 | **aceita** (201) | RC04: não há sobreposição |
| F2 | Termina 1 s antes do início | 08:00:00 | 09:59:59 | **aceita** (201) | RC04; ver [Q3](#q3) sobre segundos |
| F3 | **Termina exatamente no início** da manutenção | 08:00:00 | 10:00:00 | **EM ABERTO** | [Q1](#q1) |
| F4 | **Cruza 1 s** no início (termina 1 s depois do início) | 08:00:00 | 10:00:01 | **recusada** | RC04: há sobreposição; status 409 ou 400 conforme [Q3](#q3) |
| F5 | Dentro da manutenção | 10:30:00 | 11:30:00 | **recusada** (409) | RC04 |
| F6 | Idêntica à manutenção | 10:00:00 | 12:00:00 | **recusada** (409) | RC04 |
| F7 | Cobre a manutenção inteira | 09:00:00 | 13:00:00 | **recusada** (409) | RC04 |
| F8 | **Cruza 1 s** no fim (começa 1 s antes do fim) | 11:59:59 | 13:00:00 | **recusada** | RC04: há sobreposição; status 409 ou 400 conforme [Q3](#q3) |
| F9 | **Começa exatamente no fim** da manutenção | 12:00:00 | 14:00:00 | **EM ABERTO** | [Q1](#q1) |
| F10 | Começa 1 s depois do fim | 12:00:01 | 14:00:00 | **aceita** (201) | RC04; ver [Q3](#q3) |
| F11 | Totalmente depois | 13:00:00 | 14:00:00 | **aceita** (201) | RC04 |

> **Sobre F3 e F9.** O schema atual (V1) **aceita** esses dois casos, porque usa o mesmo intervalo
> semiaberto para reservas e manutenções. Isso foi uma escolha técnica herdada da regra
> reserva × reserva, **não uma decisão de negócio** sobre manutenção. Os testes desses dois casos
> só serão escritos depois que a Q1 for respondida.

```gherkin
# language: pt
Funcionalidade: Recurso em manutenção não pode ser reservado (RF08, RC04)

  Contexto:
    Dado os usuários e recursos da seção "Dados comuns"
    E que agora é "2026-12-01T07:00:00-03:00"
    E que "adm@senac.br" registrou manutenção da "Sala 101" de "10:00:00" a "12:00:00" com motivo "Troca do projetor"

  # Só as linhas com política decidida. F3 e F9 aguardam a Q1.
  Esquema do Cenário: MN-01 Fronteiras do período de manutenção
    Quando "ana@senac.br" solicita a "Sala 101" de "<inicio>" a "<fim>"
    Então a resposta é <status>

    Exemplos: aceitas
      | caso | inicio   | fim      | status |
      | F1   | 08:00:00 | 09:00:00 | 201    |
      | F2   | 08:00:00 | 09:59:59 | 201    |
      | F10  | 12:00:01 | 14:00:00 | 201    |
      | F11  | 13:00:00 | 14:00:00 | 201    |

    Exemplos: recusadas por sobreposição
      | caso | inicio   | fim      | status |
      | F5   | 10:30:00 | 11:30:00 | 409    |
      | F6   | 10:00:00 | 12:00:00 | 409    |
      | F7   | 09:00:00 | 13:00:00 | 409    |

    # F4 e F8: a recusa está decidida; o status (409 ou 400) depende da Q3.
    Exemplos: recusadas, cruzando 1 segundo
      | caso | inicio   | fim      | status        |
      | F4   | 08:00:00 | 10:00:01 | 409 (ver Q3)  |
      | F8   | 11:59:59 | 13:00:00 | 409 (ver Q3)  |

  Cenário: MN-02 Mensagem compreensível quando o recurso está em manutenção (RF13)
    Quando "ana@senac.br" solicita a "Sala 101" de "10:30" a "11:30"
    Então a resposta é 409 com título "Conflito de horário"
    E o detalhe informa que a "Sala 101" está em manutenção de "10:00" a "12:00"
    E o detalhe não contém SQL, nome de constraint nem nome de classe
    E é registrado um conflito evitado para a "Sala 101" (RF12)

  Cenário: MN-03 Manutenção de um recurso não afeta os outros
    Quando "ana@senac.br" solicita a "Sala 102" de "10:00" a "12:00"
    Então a resposta é 201

  Cenário: MN-04 Reserva com vários recursos é tudo ou nada
    Dado que "adm@senac.br" registrou manutenção do "Projetor PAT-0012" de "08:00" a "18:00"
    Quando "ana@senac.br" solicita, numa mesma reserva, a "Sala 102" e o "Projetor PAT-0012" de "14:00" a "16:00"
    Então a resposta é 409 e o detalhe cita o "Projetor PAT-0012"
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
    E a resposta lista essa reserva como conflitante
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
| **APROVADA** | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ✅ |
| **EM_USO** | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| **CONCLUIDA** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **REJEITADA** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **CANCELADA** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **NAO_COMPARECEU** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

São 7 transições permitidas e 42 recusadas. A linha EM_USO → CANCELADA = ❌ é a RC07 aplicada à
máquina de estados: reserva iniciada não pode ser desfeita.

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
      | de         | acao                     | ator           | agora    | motivo                 | para           | horario  |
      | SOLICITADA | aprovar                  | rita@senac.br  | 07:00:00 | ok                     | APROVADA       | ocupado  |
      | SOLICITADA | rejeitar                 | rita@senac.br  | 07:00:00 | sala em prova          | REJEITADA      | liberado |
      | SOLICITADA | cancelar                 | ana@senac.br   | 07:00:00 | aula remarcada         | CANCELADA      | liberado |
      | APROVADA   | cancelar                 | ana@senac.br   | 07:00:00 | aula remarcada         | CANCELADA      | liberado |
      | APROVADA   | cancelar                 | adm@senac.br   | 07:00:00 | evento institucional   | CANCELADA      | liberado |
      | APROVADA   | registrar início de uso  | rita@senac.br  | 08:00:00 | chave entregue         | EM_USO         | ocupado  |
      | APROVADA   | registrar não comparecimento | rita@senac.br | 09:30:00 | ninguém compareceu   | NAO_COMPARECEU | liberado |
      | EM_USO     | concluir                 | rita@senac.br  | 10:00:00 | sala devolvida         | CONCLUIDA      | ocupado  |

  # Amostra das 42 transições recusadas; o teste de unidade cobre a matriz inteira.
  Esquema do Cenário: ES-02 Transições não permitidas são recusadas
    Dado que a reserva está no estado "<de>"
    Quando um usuário com permissão tenta levá-la para "<para>"
    Então a resposta é 422 com título "Regra de negócio violada"
    E o detalhe diz, em português, que não é possível passar de "<de>" para "<para>"
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

  # Só a linha decidida. As demais fronteiras de horário estão na Q7.
  Cenário: ES-04 Cancelamento antes do horário de início é permitido
    Dado que a reserva está no estado "APROVADA"
    E que agora é "2026-12-01T07:59:59-03:00"
    Quando "ana@senac.br" cancela a reserva
    Então a reserva fica no estado "CANCELADA"

  Cenário: ES-05 Duas decisões simultâneas sobre a mesma reserva: só uma vence
    Dado que a reserva está no estado "SOLICITADA"
    Quando, no mesmo instante, "rita@senac.br" aprova e "ana@senac.br" cancela
    Então exatamente uma operação tem sucesso
    E a outra recebe 409 "Edição concorrente" ou 422 "Regra de negócio violada"
    E existe exatamente 1 registro de auditoria de transição
    E nenhuma resposta é 500
```

### Fronteiras de horário ainda não decididas

Reserva aprovada de 08:00 a 10:00.

| Ação | Agora | Resultado esperado | Pergunta |
|---|---|---|---|
| Cancelar | 07:59:59 | permitido | decidido (ES-04) |
| Cancelar | 08:00:00 (exatamente no início) | **EM ABERTO** | [Q7](#q7) |
| Cancelar | 08:30:00, ainda APROVADA (sem início de uso registrado) | **EM ABERTO** | [Q7](#q7) |
| Registrar início de uso | 07:45:00 (antes do início) | **EM ABERTO** | [Q4](#q4) |
| Registrar não comparecimento | 08:00:00 (exatamente no início) | **EM ABERTO** | [Q5](#q5) |
| Registrar não comparecimento | 07:59:59 (antes do início) | **EM ABERTO**; provavelmente recusado | [Q5](#q5) |
| Concluir | 09:00:00 (antes do fim) | **EM ABERTO** | [Q6](#q6) |

---

## Perguntas em aberto

As decisões são da equipe (ou do professor, como cliente). Respondida uma pergunta, mova os
exemplos afetados para as tabelas executáveis e registre a decisão em `docs/arquitetura.md`, seção 4.

| # | Pergunta | Cenários afetados | Comportamento atual |
|---|---|---|---|
| <a id="q1"></a>Q1 | Uma reserva que **termina exatamente no início** de uma manutenção (ou **começa exatamente no fim**) é aceita? Ou a manutenção precisa de uma margem antes/depois (limpeza, teste do equipamento)? | MN-01 (F3, F9) | O schema **aceita** (intervalo semiaberto herdado da regra reserva × reserva) |
| <a id="q2"></a>Q2 | RESPONSAVEL e ADMINISTRADOR podem **criar** reservas? O enunciado só atribui isso ao SOLICITANTE. | AU-04 | ✅ **Decidido:** só o SOLICITANTE cria (403 para os demais) |
| <a id="q3"></a>Q3 | Qual a **granularidade** dos horários: segundos, minutos ou blocos fixos (ex.: 15 min, aula de 50 min)? Se não for "segundos", F2, F4, F8 e F10 viram 400 (dados inválidos) em vez de 201/409. | MN-01 (F2, F4, F8, F10) | O banco aceita qualquer instante |
| <a id="q4"></a>Q4 | O RESPONSAVEL pode registrar o **início de uso** antes do horário reservado? Com quanta antecedência? | ES-01, tabela de fronteiras | Em aberto; sem regra de horário por ora |
| <a id="q5"></a>Q5 | O **não comparecimento** pode ser registrado a partir do instante exato do início ou só após uma tolerância (ex.: 15 min)? E depois do fim? | ES-01, tabela de fronteiras | ✅ **Decidido:** a partir do início, sem tolerância |
| <a id="q6"></a>Q6 | **Concluir antes do fim** é permitido? Se for, o tempo restante é liberado para outras reservas? | ES-01, tabela de fronteiras | CONCLUIDA mantém o horário ocupado até o fim |
| <a id="q7"></a>Q7 | O cancelamento é limitado pelo **relógio** (antes do horário de início, como diz a D6) ou só pelo **estado** (antes de EM_USO, leitura literal da RC07)? Uma reserva APROVADA às 08:30, sem início de uso registrado, pode ser cancelada? | ES-04, tabela de fronteiras | ✅ **Decidido:** pelo relógio; APROVADA só é cancelada antes do início |
| <a id="q8"></a>Q8 | Uma reserva SOLICITADA pode ser **aprovada depois do horário de início** (ou do fim)? O que acontece com solicitações não analisadas até o início: continuam segurando o horário? (Como não há agendador automático, D5, elas ficariam pendentes.) | AP-04 | ✅ **Decidido:** aprovar só antes do início; pendente pode ser rejeitada ou cancelada depois |
| <a id="q9"></a>Q9 | Bloqueio **ADMINISTRATIVO** segue exatamente as mesmas regras da manutenção? O RF08 só fala em manutenção. | toda a Regra 3 | O schema trata os dois tipos igual |
| <a id="q10"></a>Q10 | Uma manutenção pode ser **registrada no passado** ou **encerrada antes** do fim previsto (liberando o recurso)? | MN-06, MN-07 | Não implementado |

---

## Tipo de teste de cada cenário

**Teste de unidade:** JUnit 5 sem Spring e sem banco, rápido. Testa as regras puras do domínio
(`StatusReserva`, `Reserva`, `PeriodoReserva`) com `@ParameterizedTest` e relógio fixo.

**Teste de API:** caixa-preta, com requisição HTTP e JWT reais, banco PostgreSQL real
(Testcontainers) e constraint e triggers ativos. Verifica status, `ProblemDetail`, autorização
e o que foi gravado.

Autorização (403) só tem valor medida na API, porque quem nega é o Spring Security. Já as regras
puras são medidas na unidade, onde é barato testar todas as combinações.

| Cenário | Unidade | API | O que cada nível prova |
|---|:-:|:-:|---|
| AP-01 aprovação automática | ✅ | ✅ | Unidade: estado inicial a partir dos recursos. API: os 2 registros de auditoria gravados. |
| AP-02 restrito fica SOLICITADA e segura o horário | ✅ | ✅ | Unidade: o estado. API: 409 de outra pessoa (constraint). |
| AP-03 um restrito basta | ✅ | | Regra pura sobre a lista de recursos |
| AP-04 RESPONSAVEL aprova | ✅ | ✅ | Unidade: aprovador e auditoria. API: fluxo HTTP completo. |
| AP-05 perfis sem permissão | | ✅ | Autorização (403) |
| AP-06 rejeição libera horário | ✅ | ✅ | Unidade: estado e auditoria. API: horário realmente liberado no banco. |
| AP-07 rejeição sem motivo | | ✅ | Bean Validation + ProblemDetail com o campo |
| AU-01 dona não decide | | ✅ | Autorização (403) |
| AU-02 token adulterado | | ✅ | A assinatura do JWT protege o perfil (401) |
| AU-03 mass assignment | | ✅ | `fail-on-unknown-properties` + DTO |
| AU-04 RESPONSAVEL não se autoaprova (após Q2) | ✅ | ✅ | Unidade: `Reserva.aprovar` recusa o próprio solicitante. API: 403. |
| MN-01 tabela de fronteiras | ✅ | ✅ | Unidade: `PeriodoReserva.sobrepoe` com todos os casos. API: os mesmos casos contra a constraint real. |
| MN-02 mensagem compreensível | | ✅ | ProblemDetail sem detalhe interno + conflito evitado gravado |
| MN-03 outro recurso livre | | ✅ | Constraint por recurso |
| MN-04 tudo ou nada | | ✅ | Rollback da transação no banco real |
| MN-05 alteração para dentro da manutenção | | ✅ | Ocupação atualizada na mesma transação |
| MN-06 manutenção sobre reserva ativa | | ✅ | Constraint + listagem dos conflitos |
| MN-07 reservas inativas não impedem | | ✅ | Ocupações não bloqueantes |
| MN-08 só ADMINISTRADOR | | ✅ | Autorização (403) |
| MN-09 reserva × manutenção simultâneas | | ✅ | Concorrência real (`ExecutorService` + `CountDownLatch`) |
| ES-01 transições permitidas | ✅ | ✅ | Unidade: as 7 transições, a auditoria e o relógio. API: amostra do fluxo principal ponta a ponta. |
| ES-02 transições recusadas | ✅ | ✅ | Unidade: **os 49 pares** da matriz. API: 1 ou 2 exemplos para provar o 422 em ProblemDetail. |
| ES-03 reserva alheia | | ✅ | Autorização (403) |
| ES-04 cancelar antes do início | ✅ | | Regra de tempo com `Clock.fixed` |
| ES-05 decisões simultâneas | | ✅ | `@Version` + concorrência real |

**Resumo:** 25 cenários. 10 terão teste de unidade e 23 terão teste de API; 8 deles terão os dois.

### Por que não usar Cucumber

Os cenários ficam aqui, em Gherkin, como especificação. Os testes são JUnit 5 com
`@DisplayName("MN-01 …")`. Usar Cucumber exigiria mais uma dependência, uma camada de "glue code" e
outro executor, e o ganho principal dele, ler os testes como texto, já está neste documento. Com
JUnit, todos os testes entram no mesmo relatório do Surefire/Failsafe e na mesma cobertura do JaCoCo.
