import { useCallback, useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { listCaseMembers, type MemberRef } from '../cases/api';
import { isForbidden } from '../followup/errors';
import { listCaseDocuments, type CaseDocument } from '../operations/documents/documentsApi';
import {
  createDeadline,
  listCaseDeadlines,
  listRules,
  listSourceOptions,
  proposeDeadline,
  type Deadline,
  type DeadlineRule,
  type NewDeadline,
  type SourceOption,
  type SourceType,
} from './api';
import { DeadlineCard } from './DeadlineCard';
import { NewDeadlineForm } from './DeadlineForms';
import { failureKey } from './errors';

interface Props {
  caseId: string;
  /** Holds DEADLINE_MANAGE and the case is not archived. */
  editable: boolean;
  /** Holds DEADLINE_APPROVE. */
  canApprove: boolean;
  /** Changes the case page's generation so the summary and the timeline are read again. */
  onChanged?: () => void;
}

/** Section 10: the legal deadlines of this case, with approval, completion and (only from an approved rule) proposals. */
export function CaseDeadlinesSection({ caseId, editable, canApprove, onChanged }: Props) {
  const { t } = useI18n();
  const id = useId();
  const [deadlines, setDeadlines] = useState<Deadline[] | null>(null);
  const [hidden, setHidden] = useState(false);
  const [members, setMembers] = useState<MemberRef[]>([]);
  const [documents, setDocuments] = useState<CaseDocument[]>([]);
  const [rules, setRules] = useState<DeadlineRule[]>([]);
  const [sources, setSources] = useState<Partial<Record<SourceType, SourceOption[]>>>({});
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);
  const [generation, setGeneration] = useState(0);
  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
    onChanged?.();
  }, [onChanged]);

  useEffect(() => {
    const controller = new AbortController();
    listCaseDeadlines(caseId, controller.signal).then(
      (found) => {
        if (!controller.signal.aborted) setDeadlines(found);
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        if (isForbidden(error)) setHidden(true);
        else setMessage({ key: 'wp4.failed', failed: true });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, generation]);

  useEffect(() => {
    const controller = new AbortController();
    const quiet = () => {
      // The pickers stay empty; the deadline can still be typed as free text.
    };
    listCaseDocuments(caseId, controller.signal).then((found) => {
      if (!controller.signal.aborted) setDocuments(found);
    }, quiet);
    if (editable) {
      listCaseMembers(controller.signal).then((found) => {
        if (!controller.signal.aborted) setMembers(found);
      }, quiet);
      listRules(controller.signal).then((found) => {
        if (!controller.signal.aborted) setRules(found.filter((r) => r.status === 'APPROVED'));
      }, quiet);
    }
    return () => {
      controller.abort();
    };
  }, [caseId, editable]);

  const chooseType = (type: SourceType) => {
    if (type === 'TEXT') return;
    listSourceOptions(caseId, type, new AbortController().signal).then(
      (found) => { setSources((current) => ({ ...current, [type]: found })); },
      () => { setSources((current) => ({ ...current, [type]: [] })); },
    );
  };

  if (hidden) return null;

  const attempt = async (action: () => Promise<unknown>, done: MessageKey): Promise<boolean> => {
    setMessage(null);
    try {
      await action();
      setMessage({ key: done, failed: false });
      reload();
      return true;
    } catch (error) {
      setMessage({ key: failureKey(error), failed: true });
      return false;
    }
  };

  return (
    <section aria-labelledby={`${id}-h`} className="wp3-section">
      <h2 id={`${id}-h`}>{t('wp4.title')}</h2>
      {message === null ? null : (
        <p className={message.failed ? 'case-alert' : 'case-notice'} role={message.failed ? 'alert' : 'status'}>
          {t(message.key)}
        </p>
      )}
      {deadlines === null ? null : deadlines.length === 0 ? (
        <p>{t('wp4.none')}</p>
      ) : (
        <ul className="wp3-list">
          {deadlines.map((deadline) => (
            <DeadlineCard
              key={`${deadline.id}-${String(deadline.version)}`}
              deadline={deadline}
              editable={editable}
              canApprove={canApprove}
              members={members}
              documents={documents}
              onChanged={(key) => {
                setMessage({ key, failed: false });
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
        <NewDeadlineForm
          members={members}
          rules={rules}
          sources={(type) => sources[type] ?? null}
          onChooseType={chooseType}
          onManual={(input: NewDeadline) => attempt(() => createDeadline(caseId, input), 'wp4.created')}
          onPropose={(input: NewDeadline, ruleId: string) => attempt(() => proposeDeadline(caseId, input, ruleId), 'wp4.proposed')}
        />
      )}
    </section>
  );
}
