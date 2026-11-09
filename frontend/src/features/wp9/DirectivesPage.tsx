import { type SyntheticEvent, useContext, useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { AuthContext } from '../auth/AuthContext';
import { formatDate } from '../cases/format';
import { casePath } from '../cases/paths';
import {
  changeDirectiveStatus,
  createDirective,
  DIRECTIVE_STATUSES,
  EMPTY_DIRECTIVE_FILTER,
  labelOf,
  listValues,
  searchDirectives,
  type Directive,
  type DirectiveFilter,
  type DirectivePage,
  type DirectiveStatus,
  type ListValue,
} from './api';
import { DirectiveForm } from './DirectiveForm';
import { failureKey } from './errors';
import './wp9.css';

type Load = { kind: 'loading' } | { kind: 'forbidden' } | { kind: 'failed' } | { kind: 'loaded'; page: DirectivePage };

/** The register of administrative decisions and assignments (section 25); the server lists only directives of visible cases. */
export function DirectivesPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('wp9.directive.title'));
  const id = useId();
  const state = useContext(AuthContext)?.state;
  const me = state?.status === 'signedIn' ? state.me : null;
  const manage = me?.permissions.includes('DIRECTIVE_MANAGE') ?? false;
  const [draft, setDraft] = useState<DirectiveFilter>(EMPTY_DIRECTIVE_FILTER);
  const [applied, setApplied] = useState<DirectiveFilter>(EMPTY_DIRECTIVE_FILTER);
  const [generation, setGeneration] = useState(0);
  const [result, setResult] = useState<{ key: string; load: Load } | null>(null);
  const [kinds, setKinds] = useState<ListValue[]>([]);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);
  const key = JSON.stringify([applied, generation]);
  const load: Load = result?.key === key ? result.load : { kind: 'loading' };

  useEffect(() => {
    const controller = new AbortController();
    searchDirectives(applied, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) setResult({ key, load: { kind: 'loaded', page } });
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        setResult({ key, load: error instanceof ApiError && error.status === 403 ? { kind: 'forbidden' } : { kind: 'failed' } });
      },
    );
    return () => {
      controller.abort();
    };
  }, [applied, key]);

  useEffect(() => {
    listValues('DIRECTIVE_KIND').then(setKinds, () => {
      setKinds([]);
    });
  }, []);

  if (load.kind === 'forbidden') return <AccessDeniedPage />;

  const set = <K extends keyof DirectiveFilter>(field: K, value: DirectiveFilter[K]) => {
    setDraft((current) => ({ ...current, [field]: value }));
  };
  const submit = (event: SyntheticEvent) => {
    event.preventDefault();
    setApplied({ ...draft, page: 0 });
  };
  const go = (page: number) => {
    setApplied({ ...applied, page });
  };

  const add = async (input: Parameters<typeof createDirective>[0]): Promise<boolean> => {
    setMessage(null);
    try {
      await createDirective(input);
      setMessage({ key: 'wp9.directive.added', failed: false });
      setGeneration((n) => n + 1);
      return true;
    } catch (error) {
      setMessage({ key: failureKey(error), failed: true });
      return false;
    }
  };

  const move = async (directive: Directive, status: DirectiveStatus, note: string) => {
    setMessage(null);
    try {
      await changeDirectiveStatus(directive, status, note);
      setMessage({ key: 'wp9.directive.moved', failed: false });
      setGeneration((n) => n + 1);
    } catch (error) {
      setMessage({ key: failureKey(error), failed: true });
    }
  };

  return (
    <>
      <h1>{t('wp9.directive.title')}</h1>
      <p>{t('wp9.directive.intro')}</p>
      <form className="wp9-filters" role="search" aria-label={t('wp9.directive.filters')} noValidate onSubmit={submit}>
        <div className="case-field wp9-wide">
          <label htmlFor={`${id}-q`}>{t('wp9.directive.q')}</label>
          <input
            id={`${id}-q`}
            type="search"
            maxLength={200}
            value={draft.q}
            onChange={(e) => {
              set('q', e.target.value);
            }}
          />
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-status`}>{t('wp9.directive.statusField')}</label>
          <select
            id={`${id}-status`}
            value={draft.status}
            onChange={(e) => {
              set('status', e.target.value);
            }}
          >
            <option value="">{t('wp9.any')}</option>
            {DIRECTIVE_STATUSES.map((status) => (
              <option key={status} value={status}>
                {t(`wp9.directive.status.${status}`)}
              </option>
            ))}
          </select>
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-kind`}>{t('wp9.directive.kind')}</label>
          <select
            id={`${id}-kind`}
            value={draft.kind}
            onChange={(e) => {
              set('kind', e.target.value);
            }}
          >
            <option value="">{t('wp9.any')}</option>
            {kinds.map((kind) => (
              <option key={kind.id} value={kind.code}>
                {labelOf(kinds, kind.code, locale)}
              </option>
            ))}
          </select>
        </div>
        <div className="case-field">
          <label>
            <input
              type="checkbox"
              checked={draft.overdue}
              onChange={(e) => {
                set('overdue', e.target.checked);
              }}
            />{' '}
            {t('wp9.directive.overdueOnly')}
          </label>
        </div>
        <div className="case-field">
          <label>
            <input
              type="checkbox"
              checked={draft.mine}
              onChange={(e) => {
                set('mine', e.target.checked);
              }}
            />{' '}
            {t('wp9.directive.mine')}
          </label>
        </div>
        <div className="case-actions wp9-wide">
          <button type="submit" className="primary">
            {t('wp9.search')}
          </button>
        </div>
      </form>

      {message === null ? null : (
        <p className={message.failed ? 'case-alert' : 'case-notice'} role={message.failed ? 'alert' : 'status'}>
          {t(message.key)}
        </p>
      )}
      {load.kind === 'loading' ? <p role="status">{t('wp9.loading')}</p> : null}
      {load.kind === 'failed' ? (
        <p className="case-alert" role="alert">
          {t('wp9.directive.loadFailed')}
        </p>
      ) : null}
      {load.kind !== 'loaded' ? null : load.page.items.length === 0 ? (
        <p>{t('wp9.directive.none')}</p>
      ) : (
        <>
          <p>{t('wp9.directive.count', { count: String(load.page.totalItems) })}</p>
          <ul className="wp3-list">
            {load.page.items.map((directive) => (
              <DirectiveRow
                key={directive.id}
                directive={directive}
                kindLabel={labelOf(kinds, directive.kind, locale)}
                canMove={manage || directive.addresseeUserId === me?.id}
                onMove={move}
              />
            ))}
          </ul>
          <p>
            <button
              type="button"
              disabled={load.page.page === 0}
              onClick={() => {
                go(load.page.page - 1);
              }}
            >
              {t('wp9.previous')}
            </button>{' '}
            {t('wp9.pageOf', { page: String(load.page.page + 1), pages: String(Math.max(load.page.totalPages, 1)) })}{' '}
            <button
              type="button"
              disabled={load.page.page + 1 >= load.page.totalPages}
              onClick={() => {
                go(load.page.page + 1);
              }}
            >
              {t('wp9.next')}
            </button>
          </p>
        </>
      )}
      {!manage ? null : <DirectiveForm kinds={kinds} onSubmit={add} />}
    </>
  );
}

interface RowProps {
  directive: Directive;
  kindLabel: string;
  canMove: boolean;
  onMove: (directive: Directive, status: DirectiveStatus, note: string) => Promise<void>;
}

function DirectiveRow({ directive, kindLabel, canMove, onMove }: RowProps) {
  const { t, locale } = useI18n();
  const id = useId();
  const [next, setNext] = useState('');
  const [note, setNote] = useState('');
  return (
    <li aria-label={directive.directiveNo}>
      <p>
        <strong>{directive.directiveNo}</strong> — {directive.title}
        <span className="wp3-badge" data-status={directive.status}>
          {t(`wp9.directive.status.${directive.status}`)}
        </span>
        {!directive.overdue ? null : (
          <span className="wp3-badge" data-status="OVERDUE">
            {t('wp9.directive.overdue')}
          </span>
        )}
      </p>
      <p>
        <Link to={withLang(casePath(directive.caseId), locale)}>
          {directive.caseReferenceNo} {directive.caseTitle}
        </Link>
        {directive.kind === null ? null : ` · ${kindLabel}`}
      </p>
      <dl className="wp9-facts">
        <dt>{t('wp9.directive.date')}</dt>
        <dd>{formatDate(directive.directiveDate, locale)}</dd>
        <dt>{t('wp9.directive.source')}</dt>
        <dd>
          {directive.source}
          {directive.sourceReferenceNo === null ? null : ` (${directive.sourceReferenceNo})`}
        </dd>
        <dt>{t('wp9.directive.addressee')}</dt>
        <dd>{directive.addresseeName}</dd>
        <dt>{t('wp9.directive.due')}</dt>
        <dd>{directive.dueDate === null ? t('wp9.notSpecified') : formatDate(directive.dueDate, locale)}</dd>
      </dl>
      {directive.details === null ? null : <p>{directive.details}</p>}
      {directive.statusNote === null ? null : <p className="case-field-hint">{directive.statusNote}</p>}
      {!canMove ? null : (
        <form
          className="case-form"
          aria-label={t('wp9.directive.moveForm', { number: directive.directiveNo })}
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            if (next === '') return;
            void onMove(directive, next as DirectiveStatus, note).then(() => {
              setNext('');
              setNote('');
            });
          }}
        >
          <div className="case-field">
            <label htmlFor={`${id}-next`}>{t('wp9.directive.moveTo')}</label>
            <select
              id={`${id}-next`}
              value={next}
              onChange={(e) => {
                setNext(e.target.value);
              }}
            >
              <option value="">{t('wp9.choose')}</option>
              {DIRECTIVE_STATUSES.filter((status) => status !== directive.status).map((status) => (
                <option key={status} value={status}>
                  {t(`wp9.directive.status.${status}`)}
                </option>
              ))}
            </select>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-note`}>{t('wp9.directive.note')}</label>
            <input
              id={`${id}-note`}
              type="text"
              maxLength={1000}
              value={note}
              onChange={(e) => {
                setNote(e.target.value);
              }}
            />
          </div>
          <div className="case-actions">
            <button type="submit" disabled={next === ''}>
              {t('wp9.directive.apply')}
            </button>
          </div>
        </form>
      )}
    </li>
  );
}
