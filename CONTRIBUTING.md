# Como contribuir

Fluxo de trabalho da equipe. Ele existe para gerar **evidência de qualidade e de processo**
(revisões, histórico de TDD, CI), que faz parte da avaliação. Na dúvida, pergunte no grupo antes
de improvisar.

## Resumo

1. Crie uma **branch** a partir da `main` atualizada.
2. Desenvolva com **testes primeiro** e **commits pequenos** no padrão Conventional Commits.
3. Rode `./mvnw clean verify` localmente.
4. Abra um **Pull Request**, com o CI verde e a RTM atualizada.
5. **Outro integrante** revisa e aprova (obrigatório).
6. Merge com **"Create a merge commit"**. Nunca squash, nunca rebase.

Ninguém faz push direto na `main`.

## 1. Branches

Uma branch por funcionalidade ou correção, criada a partir da `main` atualizada:

```bash
git switch main && git pull --ff-only
git switch -c feat/aprovacao-reserva
```

| Prefixo | Uso | Exemplo |
|---|---|---|
| `feat/` | Nova funcionalidade | `feat/criar-reserva-sala` |
| `fix/` | Correção de defeito ou vulnerabilidade | `fix/sonar-vulnerabilidades` |
| `test/` | Só testes | `test/carga-jmeter` |
| `docs/` | Só documentação | `docs/etapa-4-documentacao` |
| `ci/` | Pipeline e build | `ci/sonarcloud-organizacao` |
| `chore/` | Manutenção sem efeito no comportamento | `chore/aiox` |

Nomes em português, minúsculos, com hífens. Branches curtas (poucos dias) evitam conflitos.

## 2. Commits: Conventional Commits

```
<tipo>(<escopo opcional>): <descrição no imperativo, em português>

<corpo opcional: o PORQUÊ da mudança>
```

- **Tipos:** `feat`, `fix`, `test`, `refactor`, `docs`, `ci`, `chore`, `build`, `perf`.
- **Escopos usados:** `reserva`, `db`, `seguranca`, `ci`, `testes`.
- **Exemplos reais do projeto:**
  - `test(reserva): RC03 exige 1 reserva aceita entre 20 simultâneas (red)`
  - `fix(seguranca): remove senha e segredo JWT do código (java:S6437)`

### Commits de TDD

O histórico precisa mostrar **teste falhando → implementação → refatoração**. Um commit por passo,
com o sufixo do passo na descrição:

| Passo | Tipo do commit | Sufixo | Estado do build |
|---|---|---|---|
| Red | `test(...)` | `(red)` | Falha **só** no teste novo; o corpo do commit registra a falha observada |
| Green | `feat(...)` ou `fix(...)` | `(green)` | Tudo verde |
| Refactor | `refactor(...)` | — | Tudo verde, sem mudar comportamento |

Commits *red* são esperados dentro da branch. Na `main`, o build tem que estar verde, o que é
garantido porque o merge só acontece com o CI aprovado.

**Só faça commit de green ou refactor se o build passou.** Encadeie pelo código de saída do Maven,
não pela saída filtrada:

```bash
./mvnw clean verify && git commit ...
```

## 3. Antes de abrir o PR

- [ ] `./mvnw clean verify` passa localmente (testes + cobertura ≥ 80% linhas / 70% branches).
- [ ] Toda regra nova tem teste. **Nada de teste trivial** só para subir a cobertura.
- [ ] Persistência testada contra **PostgreSQL real** (Testcontainers), nunca H2.
- [ ] Mocks **apenas** em testes de unidade isolados.
- [ ] [docs/rtm.md](docs/rtm.md) atualizada: testes, evidência e status do requisito.
- [ ] Decisão de arquitetura nova ou alterada registrada em [docs/arquitetura.md](docs/arquitetura.md).
- [ ] Nenhum segredo no código. Credenciais vão no `.env` (fora do git; modelo em `.env.exemplo`).
- [ ] Migration nova em vez de editar migration já commitada (Flyway).

## 4. Pull Request

- **Título** no padrão Conventional Commits.
- **Descrição:** o que muda, por quê, quais requisitos (RF/RC) afeta e como foi testado.
- **CI obrigatório:** o check *Build, testes e qualidade* (build, testes, cobertura e Quality Gate do
  SonarCloud) precisa estar verde.

## 5. Revisão

**Pelo menos 1 aprovação de outro integrante** antes do merge. Quem abriu o PR não aprova o
próprio PR. A proteção da `main` (*Settings › Branches*) deve exigir 1 aprovação e o check de CI,
para que a regra não dependa só de disciplina.

O revisor verifica:

- [ ] Os testes cobrem as fronteiras da regra (ex.: intervalos que só se tocam, duração zero).
- [ ] Mensagens de erro em português, em ProblemDetail, sem detalhe interno (SQL, classe, stack trace).
- [ ] Autorização por perfil correta (quem pode e quem **não** pode).
- [ ] Camadas respeitadas (o `ArquiteturaTest` ajuda, mas não cobre tudo).
- [ ] A RTM reflete o que o PR realmente entrega.

Peça mudanças com comentários específicos. Discordância de decisão vai para o grupo e, se for
mantida, para `docs/arquitetura.md`.

## 6. Merge

Use **"Create a merge commit"**. **Não use "Squash and merge" nem "Rebase and merge"**: o squash
junta os commits *red → green → refactor* em um só e **apaga a evidência de TDD**; o rebase reescreve
o histórico. Depois do merge, a branch pode ser apagada.

## Ambiente local

Ver [README](README.md#como-rodar-localmente). Pontos que já causaram problema:

- **"Unresolved compilation problems" nos testes:** a extensão Java do VS Code compila na mesma pasta
  `target/`. Rode `./mvnw clean verify`.
- **`mvnw` sem permissão no CI (exit code 126):** arquivos executáveis criados no Windows entram no git
  sem o bit de execução. Corrija com `git update-index --chmod=+x <arquivo>`.
- **Ferramentas pessoais de IA** (ex.: AIOX) ficam **só na sua máquina**: as pastas delas estão no
  `.gitignore` e não devem ser commitadas.
