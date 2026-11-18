UPDATE mediacoes
SET estado = 'falha_entrega'
WHERE estado = 'decisao_pendente'
  AND tentativas >= 10;
