# Evidência de TDD estrito: regras de aprovação, manutenção e estados

Prática de TDD estrito sobre as quatro regras de
[`bdd-aprovacao-bloqueio.md`](bdd-aprovacao-bloqueio.md): um cenário por vez, um único teste JUnit 5
por ciclo, `./mvnw -B test` vermelho **por asserção**, commit `test(red): <cenário>`, o mínimo para a
suíte inteira ficar verde, commit `feat(green): <cenário>`, e só então `refactor: <motivo>` sem mudar
comportamento.

## Contexto: por que uma reconstrução

As quatro regras já estavam implementadas e testadas quando esta prática foi pedida. Um teste novo
para elas passaria de primeira, e pular o vermelho é proibido. Por isso a prática foi feita na branch
`pratica/tdd-estrito`, assim:

1. **Preparação** ([`af5d90f`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/af5d90f)):
   as regras foram **retiradas** do código, que continuou compilando:
   - `Reserva`: aprovação automática, aprovar, rejeitar, cancelar, iniciar uso, não comparecimento,
     concluir e reaprovação após alteração;
   - `StatusReserva`: tabela de transições;
   - `PeriodoReserva`: sobreposição;
   - `Bloqueio`: exigência de ADMINISTRADOR e encerramento antecipado;
   - `AgendaDeRecursos`: mensagens de manutenção e de bloqueio.

   Saíram também os 7 testes de unidade antigos dessas regras.
2. **27 ciclos** red → green → refactor (tabela abaixo) reconstruíram as regras, um cenário por vez.
3. **Prova de equivalência**
   ([`7a0ff10`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/7a0ff10)):
   - os 7 testes antigos voltaram **exatamente como estavam na `main`, sem nenhuma alteração**, e
     passaram junto com os novos;
   - `./mvnw -B verify` completo: **366 testes de unidade e 261 de integração, 0 falhas**.

Isto é uma **reconstrução para a prática**, não o desenvolvimento original das regras. O
desenvolvimento original também foi feito em TDD (commits `test(...)(red)` e `feat(...)(green)` no
histórico da `main`), mas com vários testes por commit e outro formato de mensagem.

## Regras do prompt e como foram cumpridas

| Regra | Como foi cumprida |
|---|---|
| Um cenário por vez, um único teste JUnit 5 com nome que descreve condição e resultado | Cada commit `test(red)` adiciona **um** método de teste (às vezes `@ParameterizedTest`, com os exemplos do próprio cenário). Ex.: `reservaComRecursoRestritoFicaSolicitadaESeguraOHorario`. |
| `./mvnw -B test` vermelho pela razão esperada (asserção, não compilação ou configuração) | Nos 27 vermelhos, `Errors: 0`: todas as falhas foram `AssertionFailedError` ou `AssertionError` (coluna "Saída vermelha"). Quando a falha natural seria uma exceção do domínio (MN-11 antes do início), o teste usa `assertThatCode(...).doesNotThrowAnyException()`, que a transforma em falha de asserção. |
| O mínimo para a suíte inteira ficar verde | Passos mínimos de verdade. Exemplos: o AP-01 aprovava **toda** reserva automaticamente, e o AP-02 obrigou a olhar o recurso. O AP-02 olhava só o **primeiro** recurso, e o AP-03 obrigou a olhar todos. O AU-01 recusava só o perfil SOLICITANTE, e o AP-05 obrigou a exigir o RESPONSAVEL. |
| Refatorar nomes e duplicação sem mudar comportamento | 20 commits `refactor:`, todos com `./mvnw -B test` verde. Quando não havia nada a melhorar, **não** foi feito commit vazio (coluna "Refactor" com —). |
| Proibido vários testes de uma vez, alterar teste para passar ou pular o vermelho | Nenhum teste foi alterado para passar. Os testes só mudaram em commits `refactor:` (auxiliares, imports), sempre já verdes. Ver o teste **descartado** em "Incidentes". |
| `Clock` nas regras de manutenção; nunca `Instant.now()` escondido nem `Thread.sleep` | O "agora" de todo teste de regra de tempo vem de `Clock.fixed(...)` (auxiliar `relogioAs("10:30")` nos de manutenção). Em produção, o `Clock` é um bean (`ClockConfig`) injetado no caso de uso da manutenção (`GerenciarBloqueios`), que passa `clock.instant()` às regras. Não há `Instant.now()` no código de produção nem `Thread.sleep` nos testes (verificado com `grep`). O único `Instant.now()` dos testes está no `GeradorTokenTeste`, que emite JWT para os testes de integração: o servidor valida a expiração pelo relógio real, e não é regra de negócio. |

## Tabela: cenário → red → green → refactor → saída

"Saída vermelha" é o resumo do `./mvnw -B test` no commit red, mais a asserção que falhou. "Saída
verde" é o resumo no commit green (suíte de unidade inteira).

| # | Cenário | Red | Green | Refactor | Saída vermelha | Saída verde |
|---|---|---|---|---|---|---|
| 1 | AP-01 reserva só com recursos não restritos nasce aprovada pelo sistema | [`53ad044`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/53ad044) | [`4e41274`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/4e41274) | [`a2ede3c`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/a2ede3c) | `Tests run: 133, Failures: 1, Errors: 0` · AssertionFailedError: _expected: APROVADA_ | `Tests run: 133, Failures: 0, Errors: 0` |
| 2 | AP-02 reserva com recurso restrito fica solicitada e segura o horário | [`3a52dbc`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/3a52dbc) | [`863c418`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/863c418) | [`31c9944`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/31c9944) | `Tests run: 134, Failures: 1, Errors: 0` · AssertionFailedError: _expected: SOLICITADA_ | `Tests run: 134, Failures: 0, Errors: 0` |
| 3 | AP-03 basta um recurso restrito na lista para a reserva aguardar aprovação | [`df11d86`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/df11d86) | [`59147a3`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/59147a3) | [`821130c`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/821130c) | `Tests run: 135, Failures: 1, Errors: 0` · AssertionFailedError: _expected: SOLICITADA_ | `Tests run: 135, Failures: 0, Errors: 0` |
| 4 | AP-04 responsável aprova reserva de recurso restrito | [`86337b7`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/86337b7) | [`2b0b70c`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/2b0b70c) | [`4f47fa5`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/4f47fa5) | `Tests run: 136, Failures: 1, Errors: 0` · AssertionFailedError: _expected: APROVADA_ | `Tests run: 136, Failures: 0, Errors: 0` |
| 5 | AU-01 dona da reserva com perfil solicitante não aprova nem rejeita | [`13f5603`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/13f5603) | [`35c2312`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/35c2312) | [`b4949d5`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/b4949d5) | `Tests run: 138, Failures: 2, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 138, Failures: 0, Errors: 0` |
| 6 | AP-05 somente o perfil responsável aprova | [`d03143c`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/d03143c) | [`4bec2ab`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/4bec2ab) | [`bb40afa`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/bb40afa) | `Tests run: 140, Failures: 1, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 140, Failures: 0, Errors: 0` |
| 7 | AU-04 responsável não aprova reserva que ele mesmo solicitou | [`4e62a11`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/4e62a11) | [`77591f6`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/77591f6) | [`623882f`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/623882f) | `Tests run: 141, Failures: 1, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 141, Failures: 0, Errors: 0` |
| 8 | AP-06 responsável rejeita com motivo e o horário é liberado | [`0fda361`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/0fda361) | [`f5d9ebf`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/f5d9ebf) | [`b1172d5`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/b1172d5) | `Tests run: 142, Failures: 1, Errors: 0` · AssertionFailedError: _expected: REJEITADA_ | `Tests run: 142, Failures: 0, Errors: 0` |
| 9 | AP-07 rejeição sem motivo é recusada | [`681be7e`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/681be7e) | [`94ff56a`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/94ff56a) | [`eac203d`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/eac203d) | `Tests run: 145, Failures: 3, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 145, Failures: 0, Errors: 0` |
| 10 | AP-08 aprovação só antes do horário de início | [`5ebdb90`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/5ebdb90) | [`71a499e`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/71a499e) | [`9b90665`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/9b90665) | `Tests run: 148, Failures: 2, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 148, Failures: 0, Errors: 0` |
| 11 | ES-01 transições permitidas mudam o estado e registram a mudança | [`02b2958`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/02b2958) | [`61081df`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/61081df) | — | `Tests run: 156, Failures: 6, Errors: 0` · AssertionFailedError: _expected: CANCELADA_ | `Tests run: 156, Failures: 0, Errors: 0` |
| 12 | ES-02 transições não permitidas são recusadas sem registrar mudança | [`8d8975d`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/8d8975d) | [`d374ce3`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/d374ce3) | [`e4c52ee`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/e4c52ee) | `Tests run: 164, Failures: 8, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 164, Failures: 0, Errors: 0` |
| 13 | ES-03 outro solicitante não cancela reserva alheia | [`a58d65f`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/a58d65f) | [`085bf05`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/085bf05) | [`cc62753`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/cc62753) | `Tests run: 165, Failures: 1, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 165, Failures: 0, Errors: 0` |
| 14 | ES-04 reserva aprovada só é cancelada antes do início | [`c3f1bff`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/c3f1bff) | [`3f99349`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/3f99349) | — | `Tests run: 168, Failures: 2, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 168, Failures: 0, Errors: 0` |
| 15 | ES-06 alterar horário de reserva aprovada com recurso restrito exige nova aprovação | [`0751e62`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/0751e62) | [`0f894ac`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/0f894ac) | [`17dd009`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/17dd009) | `Tests run: 169, Failures: 1, Errors: 0` · AssertionFailedError: _expected: SOLICITADA_ | `Tests run: 169, Failures: 0, Errors: 0` |
| 16 | fronteira Q5 não comparecimento só a partir do início | [`5492103`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/5492103) | [`322dc5b`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/322dc5b) | — | `Tests run: 172, Failures: 1, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 172, Failures: 0, Errors: 0` |
| 17 | ES-01 só o responsável registra uso, não comparecimento e conclusão | [`8dab6fe`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/8dab6fe) | [`4633830`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/4633830) | — | `Tests run: 175, Failures: 3, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 175, Failures: 0, Errors: 0` |
| 18 | AU-04 responsável também não rejeita reserva que ele mesmo solicitou | [`6ce993b`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/6ce993b) | [`e910275`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/e910275) | [`6ada99f`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/6ada99f) | `Tests run: 176, Failures: 1, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 176, Failures: 0, Errors: 0` |
| 19 | MN-01 fronteiras do período de manutenção | [`936b0d5`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/936b0d5) | [`d630b13`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/d630b13) | [`a843b73`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/a843b73) | `Tests run: 185, Failures: 5, Errors: 0` · AssertionFailedError: _expected: true_ | `Tests run: 185, Failures: 0, Errors: 0` |
| 20 | MN-08 somente o administrador registra manutenção | [`53d2791`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/53d2791) | [`f1ee8fd`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/f1ee8fd) | [`78c058f`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/78c058f) | `Tests run: 187, Failures: 2, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 187, Failures: 0, Errors: 0` |
| 21 | MN-11 encerrar durante a manutenção encurta até o próximo minuto cheio | [`91d9eb2`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/91d9eb2) | [`82f3d93`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/82f3d93) | [`c67594d`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/c67594d) | `Tests run: 190, Failures: 2, Errors: 0` · AssertionFailedError: _expected: PeriodoReserva[inicio=2026-12-01T13:00:00Z, fim=2026-12-01T13:38:00Z]_ | `Tests run: 190, Failures: 0, Errors: 0` |
| 22 | MN-11 encerrar antes do início desfaz a manutenção | [`a45aca8`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/a45aca8) | [`cc97590`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/cc97590) | [`2c74647`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/2c74647) | `Tests run: 192, Failures: 2, Errors: 0` · AssertionError: _Expecting code not to raise a throwable but caught_ | `Tests run: 192, Failures: 0, Errors: 0` |
| 23 | MN-11 encerrar no fim ou depois é recusado | [`cfb5c88`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/cfb5c88) | [`87c4bea`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/87c4bea) | — | `Tests run: 194, Failures: 2, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 194, Failures: 0, Errors: 0` |
| 24 | MN-11 encerrar manutenção já desfeita é recusado | [`4347cc5`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/4347cc5) | [`c98d38c`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/c98d38c) | — | `Tests run: 195, Failures: 1, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 195, Failures: 0, Errors: 0` |
| 25 | MN-08 somente o administrador encerra a manutenção | [`60d8d58`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/60d8d58) | [`92b6c2f`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/92b6c2f) | [`8f6b8e8`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/8f6b8e8) | `Tests run: 196, Failures: 1, Errors: 0` · AssertionError: _Expecting code to raise a throwable._ | `Tests run: 196, Failures: 0, Errors: 0` |
| 26 | MN-02 recusa por manutenção explica sala, horário e data | [`ffb0b8b`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/ffb0b8b) | [`383d9cc`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/383d9cc) | — | `Tests run: 197, Failures: 1, Errors: 0` · AssertionFailedError: _Expecting message to be:_ | `Tests run: 197, Failures: 0, Errors: 0` |
| 27 | MN-10 bloqueio administrativo diz que a sala está bloqueada | [`0a44bbf`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/0a44bbf) | [`fdb15bc`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/fdb15bc) | [`966870b`](https://github.com/GuilhermeAFSantos/organizacao-recursos/commit/966870b) | `Tests run: 198, Failures: 1, Errors: 0` · AssertionFailedError: _Expecting message to be:_ | `Tests run: 198, Failures: 0, Errors: 0` |

Classes de teste dos ciclos:
- [`RegraAprovacaoTest`](../../src/test/java/br/senac/qs/recursos/domain/model/RegraAprovacaoTest.java): regras 1 e 2;
- [`RegraEstadosTest`](../../src/test/java/br/senac/qs/recursos/domain/model/RegraEstadosTest.java): regra 4;
- [`RegraManutencaoTest`](../../src/test/java/br/senac/qs/recursos/domain/model/RegraManutencaoTest.java): regra 3, domínio;
- [`AgendaDeRecursosTest`](../../src/test/java/br/senac/qs/recursos/application/reserva/AgendaDeRecursosTest.java): regra 3, mensagens, com o repositório simulado.

## Ciclos além dos cenários numerados

Seis ciclos não têm número próprio no documento BDD, mas cobrem regras que ele descreve e que a
preparação tinha retirado. Sem eles, os testes antigos não voltariam verdes:

| Ciclo | De onde vem no documento BDD |
|---|---|
| Fronteira Q5 (não comparecimento só a partir do início) | Tabela "Fronteiras de horário das transições" |
| ES-01 (só o RESPONSAVEL registra uso, não comparecimento e conclusão) | Coluna "ator" do ES-01 |
| AU-04 (rejeição) | Mensagem do AU-04: "Você não pode aprovar **ou rejeitar**…" |
| MN-11 antes do início, no fim ou depois, já desfeita | Fronteiras do encerramento antecipado (Q10) |
| MN-08 (encerramento) | Mensagem do MN-08: "registrar **ou encerrar** bloqueios" |

## Cenários que não rodam em `./mvnw -B test`

O prompt manda rodar `./mvnw -B test`, que executa só os testes de unidade. Dez cenários só têm
sentido medidos na API, com HTTP, Spring Security e banco reais:

| Cenário | O que exige |
|---|---|
| AU-02 | Token adulterado → 401 |
| AU-03 | Mass assignment → 400 |
| AU-05 | 403 pelo `@PreAuthorize` |
| MN-03 a MN-07 | Ocupações e constraint no PostgreSQL |
| MN-09, ES-05 | Concorrência real |

Eles **não** foram refeitos em ciclos. Continuam cobertos pelos testes de integração existentes
(`*IT`), que rodam em `./mvnw -B verify` e passaram (261) contra as regras reconstruídas.

## Incidentes, registrados como aconteceram

1. **Teste descartado por pedir regra fora da especificação.** Escrevi um teste "encerrar duas vezes:
   a segunda é recusada" com a segunda tentativa **durante** o período já encurtado. Ele rodou
   vermelho. Ao implementar, vi que nem o MN-11 nem o código original preveem essa regra, e que ela
   exigiria guardar no banco que o bloqueio foi encerrado (coluna nova): seria requisito inventado.
   O teste **não foi alterado para passar**. Foi descartado antes do commit red, e o ciclo seguinte
   testou a regra especificada: encerrar um bloqueio **já desfeito** é recusado (`4347cc5`).
2. **Refactor que quebrou a suíte.** O refactor `jaComecou(agora)` trocou também uma checagem dentro
   de um método estático e quebrou 19 testes. O commit passou porque o script auxiliar sempre saía com
   código 0. Corrigi o script para propagar o código do Maven, refiz o refactor e reescrevi o commit
   com `--amend` (ainda local, nunca enviado). O commit válido é `9b90665`, verde.
3. **Falta de memória na máquina.** Duas execuções caíram com "insufficient memory for the Java
   Runtime Environment": uma de `./mvnw -B test` no MN-02 e a primeira do `verify` final. O computador
   estava com 1,3 a 2,4 GB livres. Não houve falha de teste. As execuções repetidas, com memória livre,
   passaram (a do MN-02 registrada na tabela é a repetida).

## Como conferir

```bash
git log --oneline af5d90f..7a0ff10            # 74 commits dos ciclos + o da restauração, na ordem
git checkout <commit red> && ./mvnw -B test   # reproduz o vermelho do cenário
git checkout 7a0ff10 && ./mvnw -B verify      # tudo verde, com os testes antigos restaurados
```
