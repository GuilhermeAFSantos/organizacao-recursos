# Sequência: criação de reserva com controle de concorrência

Fluxo de `POST /api/reservas` (RF04) com as regras RC01, RC02 e RC03. Implementado em
[`CriarReservaSala`](../src/main/java/br/senac/qs/recursos/application/reserva/CriarReservaSala.java);
decisões em [arquitetura §3](arquitetura.md).

## Caminho principal e erros

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente (SOLICITANTE)
    participant S as Spring Security
    participant RC as ReservaController
    participant UC as CriarReservaSala<br/>(@Transactional)
    participant PR as PeriodoReserva
    participant DB as PostgreSQL

    C->>S: POST /api/reservas + Bearer JWT<br/>{salaId, inicio, fim, finalidade}
    alt token ausente ou inválido
        S-->>C: 401 ProblemDetail "Não autenticado"
    end
    S->>RC: requisição autenticada
    Note over RC: Bean Validation do corpo<br/>campo desconhecido (ex.: "status") → 400
    alt perfil não é SOLICITANTE
        RC-->>C: 403 "Acesso negado"
    end
    RC->>UC: executar(comando)
    UC->>PR: new PeriodoReserva(inicio, fim)
    alt término ≤ início (RC01)
        PR-->>C: 422 "O término da reserva deve ser posterior ao início."
    end
    UC->>DB: busca solicitante ativo (e-mail do token)
    UC->>DB: SELECT sala ... FOR NO KEY UPDATE
    Note over UC,DB: trava a linha da sala até o fim da transação
    alt sala inexistente ou inativa
        UC-->>C: 404 "Sala N não encontrada."
    end
    UC->>DB: existe ocupação bloqueante sobreposta? (RC02)
    alt horário ocupado (reserva ou manutenção)
        UC-->>C: 409 "Conflito de horário"
    end
    UC->>DB: INSERT reserva + INSERT ocupacao (saveAndFlush)
    Note over DB: constraint EXCLUDE USING gist<br/>(recurso_id =, periodo &&) — garantia final
    alt constraint recusa (SQLSTATE 23P01)
        UC-->>C: 409 "Conflito de horário"
    end
    UC->>DB: COMMIT (libera o lock da sala)
    UC-->>RC: ReservaCriada
    RC-->>C: 201 {id, salaId, inicio, fim, finalidade, status: SOLICITADA}
```

## Duas solicitações simultâneas para a mesma sala e horário (RC03)

O lock da sala faz a segunda transação **esperar** a primeira terminar. Quando ela continua, a
pré-checagem já enxerga a ocupação confirmada (no isolamento `READ COMMITTED`, cada comando vê o
que já foi confirmado) e responde 409, sem deadlock e sem erro 500.

```mermaid
sequenceDiagram
    autonumber
    participant A as Transação A
    participant DB as PostgreSQL
    participant B as Transação B

    par chegam ao mesmo tempo
        A->>DB: SELECT sala 7 FOR NO KEY UPDATE
        DB-->>A: lock concedido
    and
        B->>DB: SELECT sala 7 FOR NO KEY UPDATE
        Note over B,DB: B fica BLOQUEADA esperando o lock de A
    end
    A->>DB: existe sobreposição? → não
    A->>DB: INSERT reserva + ocupacao [08:00, 10:00)
    A->>DB: COMMIT
    Note over A: responde 201 Created
    DB-->>B: lock concedido (A terminou)
    B->>DB: existe sobreposição? → SIM (vê a ocupação de A)
    B->>DB: ROLLBACK
    Note over B: responde 409 Conflito de horário
```

### Por que lock **e** constraint

| Camada | O que garante | O que acontece sem ela |
|---|---|---|
| Lock da sala (`FOR NO KEY UPDATE`) | Serializa reservas do mesmo recurso; 409 com mensagem clara | Inserções conflitantes chegam juntas à constraint, que pode entrar em **deadlock** (`40P01`) e devolver 500. Observado nos testes: 3 de 10 rodadas ([`d485436`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/d485436)) |
| Pré-checagem na aplicação | Mensagem de negócio legível | O usuário recebe só a recusa genérica do banco |
| Constraint de exclusão | A regra vale mesmo para código que esqueça o lock, SQL manual ou outra instância | Uma única falha de programação permite dupla reserva |

Evidência automatizada: [`ReservaConcorrenciaIT`](../src/test/java/br/senac/qs/recursos/web/reserva/ReservaConcorrenciaIT.java)
dispara 20 requisições simultâneas em 10 rodadas e exige exatamente 1 resposta 201, 19 respostas 409
e nenhuma 500.
