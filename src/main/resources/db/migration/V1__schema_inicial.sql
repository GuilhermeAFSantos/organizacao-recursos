-- =====================================================================================
-- V1 - Schema inicial do sistema Organização de Recursos
--
-- Decisões principais (ver docs/arquitetura.md):
--  * Datas/horas em TIMESTAMPTZ (instante absoluto, sem ambiguidade de fuso).
--  * Intervalos SEMIABERTOS [inicio, fim): uma reserva que termina às 10h NÃO conflita
--    com outra que começa às 10h.
--  * A tabela "ocupacao" é a fonte da verdade sobre "recurso X ocupado no período P",
--    tanto por reservas quanto por bloqueios de manutenção. Uma única constraint de
--    exclusão nela garante RC02 (sem sobreposição), RC03 (concorrência) e RC04 (manutenção)
--    no próprio banco, independentemente do código Java.
-- =====================================================================================

-- Necessária para combinar igualdade de BIGINT (operador B-tree) com sobreposição de
-- intervalos (operador GiST) dentro do mesmo índice de exclusão.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- -------------------------------------------------------------------------------------
-- Usuários e perfis (RF01)
-- -------------------------------------------------------------------------------------
CREATE TABLE usuario (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome        VARCHAR(120) NOT NULL,
    email       VARCHAR(160) NOT NULL,
    senha_hash  VARCHAR(100) NOT NULL,
    perfil      VARCHAR(20)  NOT NULL,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('SOLICITANTE', 'RESPONSAVEL', 'ADMINISTRADOR'))
);
-- E-mail único sem diferenciar maiúsculas/minúsculas.
CREATE UNIQUE INDEX uk_usuario_email ON usuario (lower(email));

-- -------------------------------------------------------------------------------------
-- Recursos reserváveis (RF02, RF03): herança JOINED -> recurso + sala/professor/material
-- -------------------------------------------------------------------------------------
CREATE TABLE recurso (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo        VARCHAR(20)  NOT NULL,
    nome        VARCHAR(120) NOT NULL,
    restrito    BOOLEAN      NOT NULL DEFAULT FALSE, -- RF07/RC06: exige aprovação do RESPONSAVEL
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_recurso_tipo CHECK (tipo IN ('SALA', 'PROFESSOR', 'MATERIAL'))
);
CREATE INDEX ix_recurso_tipo ON recurso (tipo) WHERE ativo;

CREATE TABLE sala (
    id          BIGINT       PRIMARY KEY REFERENCES recurso (id),
    capacidade  INTEGER      NOT NULL,
    bloco       VARCHAR(40)  NOT NULL,
    andar       INTEGER      NOT NULL,
    tipo_sala   VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_sala_capacidade CHECK (capacidade > 0),
    CONSTRAINT ck_sala_tipo CHECK (tipo_sala IN ('AULA', 'LABORATORIO', 'AUDITORIO', 'REUNIAO'))
);
CREATE INDEX ix_sala_capacidade ON sala (capacidade);

CREATE TABLE professor (
    id          BIGINT       PRIMARY KEY REFERENCES recurso (id),
    matricula   VARCHAR(30)  NOT NULL UNIQUE,
    email       VARCHAR(160),
    usuario_id  BIGINT       UNIQUE REFERENCES usuario (id) -- vínculo opcional com login
);

CREATE TABLE competencia (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome  VARCHAR(80) NOT NULL
);
CREATE UNIQUE INDEX uk_competencia_nome ON competencia (lower(nome));

CREATE TABLE professor_competencia (
    professor_id    BIGINT NOT NULL REFERENCES professor (id),
    competencia_id  BIGINT NOT NULL REFERENCES competencia (id),
    PRIMARY KEY (professor_id, competencia_id)
);
CREATE INDEX ix_professor_competencia_competencia ON professor_competencia (competencia_id);

-- Materiais são UNITÁRIOS: cada item tem patrimônio próprio (ex.: "Projetor #0012").
-- Assim a mesma regra de exclusão vale para salas, professores e materiais.
CREATE TABLE material (
    id                  BIGINT       PRIMARY KEY REFERENCES recurso (id),
    patrimonio          VARCHAR(40)  NOT NULL UNIQUE,
    tipo_material       VARCHAR(60)  NOT NULL,
    estado_conservacao  VARCHAR(20)  NOT NULL DEFAULT 'BOM',
    CONSTRAINT ck_material_estado CHECK (estado_conservacao IN ('BOM', 'REGULAR', 'DANIFICADO'))
);

-- -------------------------------------------------------------------------------------
-- Reservas (RF04, RC01, RC05)
-- -------------------------------------------------------------------------------------
CREATE TABLE reserva (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    solicitante_id  BIGINT       NOT NULL REFERENCES usuario (id),
    aprovador_id    BIGINT       REFERENCES usuario (id),
    inicio          TIMESTAMPTZ  NOT NULL,
    fim             TIMESTAMPTZ  NOT NULL,
    finalidade      VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    motivo_status   VARCHAR(500),
    version         BIGINT       NOT NULL DEFAULT 0, -- lock otimista: transições concorrentes
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- RC01 também no banco (segunda barreira, além da validação na aplicação).
    CONSTRAINT ck_reserva_periodo CHECK (fim > inicio),
    CONSTRAINT ck_reserva_status CHECK (status IN ('SOLICITADA', 'APROVADA', 'EM_USO', 'CONCLUIDA',
                                                   'REJEITADA', 'CANCELADA', 'NAO_COMPARECEU'))
);
CREATE INDEX ix_reserva_solicitante ON reserva (solicitante_id);
CREATE INDEX ix_reserva_status ON reserva (status);
CREATE INDEX ix_reserva_inicio ON reserva (inicio);

-- -------------------------------------------------------------------------------------
-- Bloqueios e manutenções (RF08, RC04)
-- -------------------------------------------------------------------------------------
CREATE TABLE bloqueio (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo            VARCHAR(20)  NOT NULL,
    motivo          VARCHAR(500) NOT NULL,
    inicio          TIMESTAMPTZ  NOT NULL,
    fim             TIMESTAMPTZ  NOT NULL,
    criado_por_id   BIGINT       NOT NULL REFERENCES usuario (id),
    criado_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_bloqueio_periodo CHECK (fim > inicio),
    CONSTRAINT ck_bloqueio_tipo CHECK (tipo IN ('MANUTENCAO', 'ADMINISTRATIVO'))
);

-- -------------------------------------------------------------------------------------
-- Ocupação: o coração do controle de conflitos (RC02, RC03, RC04)
-- -------------------------------------------------------------------------------------
CREATE TABLE ocupacao (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recurso_id   BIGINT       NOT NULL REFERENCES recurso (id),
    reserva_id   BIGINT       REFERENCES reserva (id),
    bloqueio_id  BIGINT       REFERENCES bloqueio (id),
    inicio       TIMESTAMPTZ  NOT NULL,
    fim          TIMESTAMPTZ  NOT NULL,
    -- Coluna calculada pelo próprio banco: a aplicação só grava inicio/fim.
    -- '[)' = fechado no início, aberto no fim (intervalos que só se tocam não conflitam).
    periodo      TSTZRANGE    GENERATED ALWAYS AS (tstzrange(inicio, fim, '[)')) STORED,
    -- TRUE enquanto a ocupação "segura" o horário (SOLICITADA, APROVADA, EM_USO, CONCLUIDA
    -- ou bloqueio vigente). Vira FALSE ao cancelar/rejeitar/não comparecer, liberando o horário.
    bloqueante   BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_ocupacao_periodo CHECK (fim > inicio),
    -- Toda ocupação vem de exatamente uma origem: reserva OU bloqueio.
    CONSTRAINT ck_ocupacao_origem CHECK ((reserva_id IS NULL) <> (bloqueio_id IS NULL)),
    CONSTRAINT uk_ocupacao_reserva_recurso UNIQUE (reserva_id, recurso_id),
    -- RC02/RC03/RC04: o mesmo recurso não pode ter duas ocupações bloqueantes sobrepostas.
    -- Em transações simultâneas, a segunda ESPERA a primeira terminar; se a primeira fizer
    -- commit, a segunda falha com SQLSTATE 23P01 (exclusion_violation) -> HTTP 409.
    CONSTRAINT ex_ocupacao_sem_sobreposicao
        EXCLUDE USING gist (recurso_id WITH =, periodo WITH &&) WHERE (bloqueante)
);
CREATE INDEX ix_ocupacao_reserva ON ocupacao (reserva_id);
CREATE INDEX ix_ocupacao_bloqueio ON ocupacao (bloqueio_id);

-- -------------------------------------------------------------------------------------
-- Retirada e devolução de materiais (RF09)
-- -------------------------------------------------------------------------------------
CREATE TABLE movimentacao_material (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    material_id         BIGINT       NOT NULL REFERENCES material (id),
    reserva_id          BIGINT       NOT NULL REFERENCES reserva (id),
    tipo                VARCHAR(20)  NOT NULL,
    data_hora           TIMESTAMPTZ  NOT NULL,
    estado_conservacao  VARCHAR(20)  NOT NULL,
    observacao          VARCHAR(500),
    registrado_por_id   BIGINT       NOT NULL REFERENCES usuario (id),
    CONSTRAINT ck_movimentacao_tipo CHECK (tipo IN ('RETIRADA', 'DEVOLUCAO')),
    CONSTRAINT ck_movimentacao_estado CHECK (estado_conservacao IN ('BOM', 'REGULAR', 'DANIFICADO'))
);
CREATE INDEX ix_movimentacao_material ON movimentacao_material (material_id, data_hora);
CREATE INDEX ix_movimentacao_reserva ON movimentacao_material (reserva_id);

-- -------------------------------------------------------------------------------------
-- Auditoria (RF10, RC08): somente inserção (append-only)
-- -------------------------------------------------------------------------------------
CREATE TABLE registro_auditoria (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entidade         VARCHAR(40)  NOT NULL,
    entidade_id      BIGINT       NOT NULL,
    acao             VARCHAR(40)  NOT NULL,
    estado_anterior  VARCHAR(30),
    estado_novo      VARCHAR(30),
    motivo           VARCHAR(500),
    usuario_id       BIGINT,
    usuario_email    VARCHAR(160), -- cópia para leitura histórica, mesmo se o usuário mudar
    ocorrido_em      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_auditoria_entidade ON registro_auditoria (entidade, entidade_id, ocorrido_em);

-- Impede alteração/remoção do histórico mesmo por SQL direto ou bug na aplicação.
CREATE FUNCTION fn_auditoria_somente_insercao() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'registro_auditoria é somente inserção: operação % não permitida', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER tg_auditoria_sem_update_delete
    BEFORE UPDATE OR DELETE ON registro_auditoria
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_somente_insercao();

CREATE TRIGGER tg_auditoria_sem_truncate
    BEFORE TRUNCATE ON registro_auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION fn_auditoria_somente_insercao();

-- -------------------------------------------------------------------------------------
-- Conflitos evitados (RF12). Gravado em transação separada (REQUIRES_NEW), pois a
-- transação da reserva rejeitada sofre rollback.
-- -------------------------------------------------------------------------------------
CREATE TABLE conflito_evitado (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recurso_id      BIGINT       REFERENCES recurso (id),
    solicitante_id  BIGINT       REFERENCES usuario (id),
    inicio          TIMESTAMPTZ  NOT NULL,
    fim             TIMESTAMPTZ  NOT NULL,
    origem          VARCHAR(20)  NOT NULL,
    ocorrido_em     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_conflito_origem CHECK (origem IN ('APLICACAO', 'BANCO'))
);
CREATE INDEX ix_conflito_recurso ON conflito_evitado (recurso_id, ocorrido_em);
