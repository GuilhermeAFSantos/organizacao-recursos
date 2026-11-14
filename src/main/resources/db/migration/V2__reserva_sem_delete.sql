-- =====================================================================================
-- V2 - RC07: reservas nunca são apagadas fisicamente
--
-- Decisão de projeto (docs/arquitetura.md): cancelar uma reserva é MUDANÇA DE ESTADO
-- (status = CANCELADA e ocupacao.bloqueante = FALSE), nunca DELETE. Bloqueamos o DELETE em
-- qualquer status, o que cobre com folga a RC07 ("reservas EM_USO ou depois não podem ser
-- apagadas") e preserva o histórico exigido pela RF10 e pelo relatório da RF12.
--
-- Fica no banco (e não só na aplicação) para valer também contra SQL manual ou bug futuro.
-- =====================================================================================

CREATE FUNCTION fn_reserva_sem_delete() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'reservas não podem ser apagadas (RC07): cancele a reserva em vez de removê-la'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER tg_reserva_sem_delete
    BEFORE DELETE ON reserva
    FOR EACH ROW EXECUTE FUNCTION fn_reserva_sem_delete();

-- TRUNCATE não dispara gatilhos de linha: precisa de um gatilho de instrução próprio.
CREATE TRIGGER tg_reserva_sem_truncate
    BEFORE TRUNCATE ON reserva
    FOR EACH STATEMENT EXECUTE FUNCTION fn_reserva_sem_delete();
