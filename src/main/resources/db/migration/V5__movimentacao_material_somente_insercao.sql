-- =====================================================================================
-- V5 - Histórico de retirada e devolução de materiais somente de inserção (RF09)
--
-- Risco que o RF09 controla: "dano não registrado". Se uma devolução com dano pudesse ser
-- editada ou apagada, o registro perderia o valor. Como na auditoria (V1) e nas reservas (V2),
-- o banco recusa UPDATE, DELETE e TRUNCATE, mesmo vindos de SQL manual ou de um bug na aplicação.
-- =====================================================================================
CREATE FUNCTION fn_movimentacao_somente_insercao() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'movimentacao_material é somente inserção: operação % não permitida', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER tg_movimentacao_sem_update_delete
    BEFORE UPDATE OR DELETE ON movimentacao_material
    FOR EACH ROW EXECUTE FUNCTION fn_movimentacao_somente_insercao();
CREATE TRIGGER tg_movimentacao_sem_truncate
    BEFORE TRUNCATE ON movimentacao_material
    FOR EACH STATEMENT EXECUTE FUNCTION fn_movimentacao_somente_insercao();
