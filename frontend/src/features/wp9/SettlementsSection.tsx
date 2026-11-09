import { useCallback, useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { getCaseParties, type CaseParty } from '../cases/api';
import { listCaseDocuments, type CaseDocument } from '../operations/documents/documentsApi';
import {
  createSettlement,
  EMPTY_SETTLEMENT,
  listSettlements,
  listValues,
  type ListValue,
  type Settlement,
  type SettlementInput,
} from './api';
import { failureKey, isForbidden } from './errors';
import { SettlementCard } from './SettlementCard';
import { SettlementForm } from './SettlementForm';
import './wp9.css';

interface Props {
  caseId: string;
  /** Holds SETTLEMENT_MANAGE and the case is not archived. */
  editable: boolean;
  /** Holds SETTLEMENT_APPROVE (the server also needs the right to change the case). */
  canApprove: boolean;
}

/** Section 23: settlements, waivers and conciliations of the case. Recording one never closes the case or a linked case. */
export function SettlementsSection({ caseId, editable, canApprove }: Props) {
  const { t } = useI18n();
  const id = useId();
  const [items, setItems] = useState<Settlement[] | null>(null);
  const [hidden, setHidden] = useState(false);
  const [kinds, setKinds] = useState<ListValue[]>([]);
  const [parties, setParties] = useState<CaseParty[]>([]);
  const [documents, setDocuments] = useState<CaseDocument[]>([]);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);
  const [generation, setGeneration] = useState(0);
  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    listSettlements(caseId, controller.signal).then(
      (found) => {
        if (!controller.signal.aborted) setItems(found);
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        if (isForbidden(error)) setHidden(true);
        else setMessage({ key: 'wp9.settlement.loadFailed', failed: true });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, generation]);

  useEffect(() => {
    const controller = new AbortController();
    listValues('SETTLEMENT_KIND', controller.signal).then(setKinds, () => {
      setKinds([]);
    });
    if (editable) {
      getCaseParties(caseId, controller.signal).then(setParties, () => {
        setParties([]);
      });
    }
    return () => {
      controller.abort();
    };
  }, [caseId, editable]);

  useEffect(() => {
    if (!editable) return undefined;
    const controller = new AbortController();
    listCaseDocuments(caseId, controller.signal).then(setDocuments, () => {
      setDocuments([]);
    });
    return () => {
      controller.abort();
    };
  }, [caseId, editable, generation]);

  if (hidden) return null;

  const add = async (input: SettlementInput) => {
    setMessage(null);
    try {
      await createSettlement(caseId, input);
      setMessage({ key: 'wp9.settlement.added', failed: false });
      reload();
    } catch (error) {
      setMessage({ key: failureKey(error), failed: true });
    }
  };

  return (
    <section aria-labelledby={`${id}-h`} className="wp3-section">
      <h2 id={`${id}-h`}>{t('wp9.settlement.title')}</h2>
      <p className="case-field-hint">{t('wp9.settlement.hint')}</p>
      {message === null ? null : (
        <p className={message.failed ? 'case-alert' : 'case-notice'} role={message.failed ? 'alert' : 'status'}>
          {t(message.key)}
        </p>
      )}
      {items === null ? null : items.length === 0 ? (
        <p>{t('wp9.settlement.none')}</p>
      ) : (
        <ul className="wp3-list">
          {items.map((settlement) => (
            <SettlementCard
              key={settlement.id}
              settlement={settlement}
              kinds={kinds}
              parties={parties}
              documents={documents}
              editable={editable}
              canApprove={canApprove}
              onSaved={(key) => {
                setMessage(key === null ? null : { key, failed: false });
                reload();
              }}
              onFailed={(key) => {
                setMessage({ key, failed: true });
              }}
            />
          ))}
        </ul>
      )}
      {!editable ? null : (
        <SettlementForm
          key={generation}
          initial={EMPTY_SETTLEMENT}
          kinds={kinds}
          parties={parties}
          submitLabel={t('wp9.settlement.add')}
          onSubmit={add}
        />
      )}
    </section>
  );
}
