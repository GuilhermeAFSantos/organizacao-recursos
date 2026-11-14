# Teste de carga (RNF06): plano, metas e resultados

Plano: [`jmeter/carga-reservas.jmx`](../../jmeter/carga-reservas.jmx). Metas conferidas por
[`MetasDeCarga`](../../src/test/java/br/senac/qs/recursos/carga/MetasDeCarga.java), cuja lógica é
testada em [`MetasDeCargaTest`](../../src/test/java/br/senac/qs/recursos/carga/MetasDeCargaTest.java).
No CI, o workflow [Teste de carga](../../.github/workflows/carga.yml) repete tudo a cada mudança de
código na `main` e publica o relatório HTML do JMeter como artefato (`relatorio-carga`).

## Cenário

| Grupo | Usuários | O que cada um faz, sem parar, por 2 minutos |
|---|---|---|
| Preparação | 1 | O ADMINISTRADOR cria, pela API, 20 salas, 1 sala disputada e 50 professores (SOLICITANTE). |
| Professores reservando | 50 (rampa de 30 s) | Entra uma vez; depois repete: pesquisa salas livres num horário aleatório dos próximos 180 dias → reserva uma delas → lista as próprias reservas. Pausa de 0,3 a 1 s entre as ações. |
| Disputa pelo mesmo horário | 10 | Tenta reservar **a mesma sala** num de só **15 horários** (5 dias × 3 horas), com pausa de 0,1 a 0,4 s. Mede a proteção contra dupla reserva (RC03) sob carga. |

Respostas aceitas: 2xx; ao reservar, 201 (criada) ou **409 (conflito evitado)**, que é o comportamento
correto quando outro usuário pegou o horário antes. Qualquer 5xx ou código inesperado é erro.

## Metas

| Meta | Valor | Por quê |
|---|---|---|
| Erros | **0** | Nenhuma requisição pode falhar no servidor, nem sob disputa. |
| Pesquisar salas livres, listar minhas reservas (p95) | **< 500 ms** | Consultas que o usuário faz olhando para a tela. |
| Criar reserva, inclusive disputando o mesmo horário (p95) | **< 1 s** | Escrita com lock dos recursos e auditoria; ainda deve parecer imediata. |

O p95 (95% das requisições abaixo do valor) é usado em vez da média, que esconde os casos lentos.

## Resultado da execução local (14/11/2026)

Ambiente: Intel Core i7-6700K (4 núcleos / 8 threads), 16 GB de RAM, Windows 11; aplicação em
`java -Xmx512m -jar`; PostgreSQL 17 em Docker na mesma máquina; JMeter 5.6.3 também na mesma máquina
(o gerador de carga disputa CPU com a aplicação, então os tempos reais tendem a ser menores).

| Transação | Requisições | Erros | p95 | p99 | Meta (p95) |
|---|---:|---:|---:|---:|---|
| Pesquisar salas livres | 2.652 | 0 | 15 ms | 22 ms | < 500 ms ✅ |
| Listar minhas reservas | 2.616 | 0 | 16 ms | 25 ms | < 500 ms ✅ |
| Criar reserva | 2.641 | 0 | 29 ms | 54 ms | < 1 s ✅ |
| Disputar o mesmo horário | 4.032 | 0 | 22 ms | 45 ms | < 1 s ✅ |
| Login (uma vez por usuário; BCrypt) | 60 | 0 | 100 ms | 133 ms | — |
| **Total** | **12.073** | **0** | 23 ms | 67 ms | 0 erros ✅ |

Vazão: **96 requisições/s** durante 2 minutos.

**Proteção contra dupla reserva sob carga** (conferido direto no banco ao final):

| Verificação | Resultado |
|---|---|
| Tentativas na disputa | 4.032: **15 aceitas** (201) e **4.017 recusadas** (409) |
| Reservas ativas na sala disputada | **15**: exatamente uma por horário possível |
| Pares de ocupações sobrepostas em todo o banco | **0** |
| Conflitos evitados registrados (RF12) | 4.018 (4.017 da disputa + 1 da carga), **todos com origem APLICACAO** |

Todos os conflitos foram barrados pela pré-checagem com lock; nenhum precisou da constraint do banco
(origem BANCO = 0). Isso indica que a serialização por lock dos recursos funciona sob carga, e a
constraint ficou como garantia de reserva.

## Como repetir

```bash
# 1. Aplicação em execução (ver README) e as credenciais do ADMINISTRADOR do .env
# 2. Teste de carga (o plugin baixa o JMeter; relatório em target/jmeter/reports/carga-reservas/index.html)
rm -rf target/jmeter
./mvnw -Pcarga jmeter:configure@configuration jmeter:jmeter jmeter:results \
       -Dcarga.adminEmail=admin@senac.br -Dcarga.adminSenha='<ADMIN_SENHA>'
# 3. Metas
./mvnw test-compile org.codehaus.mojo:exec-maven-plugin:3.6.4:java \
       -Dexec.classpathScope=test -Dexec.mainClass=br.senac.qs.recursos.carga.MetasDeCarga
```

Parâmetros opcionais: `-Dcarga.porta`, `-Dcarga.usuarios` (50), `-Dcarga.rampa` (30 s),
`-Dcarga.duracao` (120 s), `-Dcarga.salas` (20), `-Dcarga.disputa` (10).

## Lições do plano

- **Cabeçalho de token fora do lugar.** Na primeira versão, o `Authorization: Bearer ${token}` valia
  também para o login; antes de existir token, ia o texto literal e a API respondia 401, corretamente.
  Os cabeçalhos de token agora valem só para as requisições depois do login.
- **Carga sem disputa não testa a RC03.** A primeira execução terminou com 0 conflitos: com 20 salas
  e 180 dias, os usuários quase nunca escolhem o mesmo horário. O grupo "Disputa pelo mesmo horário"
  foi criado para forçar o caso que importa.
