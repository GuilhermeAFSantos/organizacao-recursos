-- =====================================================================================
-- V3 - Proteção contra força bruta no login (RF01)
--
-- Após 5 senhas erradas seguidas, o usuário fica bloqueado por 15 minutos. O contador e o
-- instante de desbloqueio ficam no próprio usuário, então o bloqueio vale mesmo com várias
-- instâncias da aplicação e sobrevive a reinícios.
-- =====================================================================================

ALTER TABLE usuario
    ADD COLUMN tentativas_falhas INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN bloqueado_ate     TIMESTAMPTZ,
    ADD CONSTRAINT ck_usuario_tentativas CHECK (tentativas_falhas >= 0);
