# Diagrama de contexto

Quem usa o sistema e com quais sistemas externos ele conversa. Elementos tracejados ainda não
foram implementados (ver [RTM](../RTM.md)).

```mermaid
flowchart LR
    sol["👤 SOLICITANTE<br/>professor ou coordenador<br/><i>consulta disponibilidade;<br/>cria, altera e cancela<br/>as próprias reservas</i>"]
    resp["👤 RESPONSAVEL<br/><i>aprova recursos restritos;<br/>valida docentes; registra<br/>retirada e devolução</i>"]
    adm["👤 ADMINISTRADOR<br/><i>gerencia salas, professores,<br/>materiais, usuários,<br/>bloqueios e manutenções</i>"]

    subgraph sistema["Organização de Recursos"]
        ui["Interface web responsiva<br/>(HTML estático)"]
        api["API REST<br/>Spring Boot 3 · Java 21<br/>JWT · ProblemDetail · Swagger"]
    end

    db[("PostgreSQL 17<br/>constraint de exclusão<br/>triggers de auditoria")]
    notif["Serviço de notificação<br/>(API externa; WireMock nos testes)"]

    sol --> ui
    resp --> ui
    adm --> ui
    sol -.->|"HTTP + JWT"| api
    ui -->|"HTTP + JWT"| api
    api -->|"JDBC (Flyway, JPA)"| db
    api -.->|"HTTP após o commit (RF11)"| notif

    classDef pendente stroke-dasharray: 5 5
    class notif pendente
```

| Elemento | Papel | Situação |
|---|---|---|
| Interface web | Telas para os três perfis | Página inicial publicada; telas pendentes (RF13) |
| API REST | Regras de negócio, autenticação e autorização por perfil | Em desenvolvimento ([RTM](../RTM.md)) |
| PostgreSQL | Persistência e **última barreira** das regras críticas (RC01, RC02/RC03/RC04 via constraint de exclusão, RC07 e RC08 via triggers) | Pronto (migrations V1 e V2) |
| Serviço de notificação | Avisar solicitantes sobre aprovação, rejeição e cancelamento. Chamado **depois do commit**: falha na notificação não desfaz a reserva (decisão D9) | Pendente (RF11) |

## Contexto de desenvolvimento

Fora da execução do sistema, o código passa por esta esteira de qualidade:

```mermaid
flowchart LR
    dev["👩‍💻 Integrante"] -->|"branch + PR"| gh["GitHub"]
    gh -->|"pull_request / push main"| ci["GitHub Actions<br/>./mvnw verify"]
    ci -->|"Testcontainers"| pg[("PostgreSQL<br/>descartável")]
    ci -->|"cobertura JaCoCo"| sonar["SonarCloud<br/>Quality Gate"]
    ci -->|"artefato"| rel["relatórios de teste<br/>e cobertura"]
    revisor["👤 Outro integrante"] -->|"revisão obrigatória"| gh
```
