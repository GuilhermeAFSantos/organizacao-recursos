-- =====================================================================================
-- V4 - Nome de sala único (RF02)
--
-- Duas salas com o mesmo nome ("Sala 101" e "sala 101") confundem quem reserva. A aplicação
-- verifica antes de gravar para dar uma mensagem clara; este índice é a garantia de verdade,
-- inclusive para dois cadastros simultâneos. Vale só para salas: professores já são únicos pela
-- matrícula e materiais pelo patrimônio (V1), e dois projetores podem ter o mesmo nome.
-- =====================================================================================
CREATE UNIQUE INDEX uk_recurso_nome_sala ON recurso (lower(nome)) WHERE tipo = 'SALA';
