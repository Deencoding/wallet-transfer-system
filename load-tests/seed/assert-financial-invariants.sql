\set ON_ERROR_STOP on

DO $$
DECLARE violations BIGINT;
BEGIN
  SELECT count(*) INTO violations FROM wallets WHERE available_balance < 0 OR ledger_balance < 0;
  IF violations <> 0 THEN RAISE EXCEPTION '% wallets have negative balances', violations; END IF;

  SELECT count(*) INTO violations FROM (
    SELECT j.id
    FROM journal_transactions j
    LEFT JOIN journal_entries e ON e.journal_transaction_id=j.id
    GROUP BY j.id
    HAVING count(e.id)<2 OR
      COALESCE(sum(e.amount) FILTER(WHERE e.entry_type='DEBIT'),0) <>
      COALESCE(sum(e.amount) FILTER(WHERE e.entry_type='CREDIT'),0)
  ) invalid;
  IF violations <> 0 THEN RAISE EXCEPTION '% journals are unbalanced', violations; END IF;

  SELECT count(*) INTO violations FROM (
    SELECT w.id,w.ledger_balance,w.available_balance,
      COALESCE(sum(CASE WHEN e.entry_type='CREDIT' THEN e.amount ELSE -e.amount END),0) calculated,
      COALESCE((SELECT sum(r.amount) FROM external_transfer_reservations r
        WHERE r.wallet_id=w.id AND r.status='ACTIVE'),0) reserved
    FROM wallets w JOIN ledger_accounts a ON a.wallet_id=w.id
    LEFT JOIN journal_entries e ON e.ledger_account_id=a.id
    GROUP BY w.id
  ) position
  WHERE ledger_balance<>calculated OR available_balance<>calculated-reserved;
  IF violations <> 0 THEN RAISE EXCEPTION '% wallet projections differ from ledger/reservations', violations; END IF;

  SELECT count(*) INTO violations FROM transfers t
  WHERE t.status IN('SUCCESSFUL','REVERSED') AND
    (SELECT count(*) FROM journal_transactions j
      WHERE j.source_type='TRANSFER' AND j.source_reference=t.reference)<>1;
  IF violations <> 0 THEN RAISE EXCEPTION '% completed transfers lack exactly one transfer journal', violations; END IF;

  SELECT count(*) INTO violations FROM transfers t
  WHERE t.status='REVERSED' AND
    (SELECT count(*) FROM transfer_reversals r
      WHERE r.original_transfer_id=t.id AND r.status='SUCCESSFUL')<>1;
  IF violations <> 0 THEN RAISE EXCEPTION '% reversed transfers lack exactly one successful reversal', violations; END IF;

  SELECT count(*) INTO violations FROM external_transfers e
  JOIN external_transfer_reservations r ON r.external_transfer_id=e.id
  WHERE (e.status='SUCCESSFUL' AND r.status<>'SETTLED')
     OR (e.status='FAILED' AND r.status<>'RELEASED');
  IF violations <> 0 THEN RAISE EXCEPTION '% terminal external transfers have invalid reservations', violations; END IF;

  SELECT count(*) INTO violations FROM (
    SELECT idempotency_record_id FROM transfers WHERE idempotency_record_id IS NOT NULL
    GROUP BY idempotency_record_id HAVING count(*)>1
  ) duplicate_effect;
  IF violations <> 0 THEN RAISE EXCEPTION '% idempotency records produced duplicate transfers', violations; END IF;

  SELECT count(*) INTO violations FROM outbox_events WHERE status='DEAD';
  IF violations <> 0 THEN RAISE EXCEPTION '% outbox events are dead-lettered', violations; END IF;
END $$;

SELECT 'financial invariants passed' AS result,
       (SELECT count(*) FROM transfers) AS transfers,
       (SELECT count(*) FROM journal_transactions) AS journals,
       (SELECT count(*) FROM outbox_events WHERE status='DEAD') AS dead_outbox_events;
