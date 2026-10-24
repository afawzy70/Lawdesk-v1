import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { getCase, updateCase, type LegalCase, type NewCase } from './api';
import { CaseForm, type Failure } from './CaseForm';
import type { CreatedState } from './CaseCreatePage';
import { CASES_PATH } from './paths';
import { fieldErrors, isStatus, problemMessage } from './problems';

type Load = { kind: 'loading' } | { kind: 'failed'; message: MessageKey } | { kind: 'loaded'; legalCase: LegalCase };

function explain(error: unknown): Failure {
  if (isStatus(error, 409)) {
    return { message: 'cases.edit.conflict', fields: {}, conflict: true };
  }
  if (isStatus(error, 422, 'CASE_READ_ONLY')) {
    return { message: 'cases.edit.readOnly', fields: {} };
  }
  if (isStatus(error, 400)) {
    return { message: 'cases.form.invalid', fields: fieldErrors(error) };
  }
  return { message: problemMessage(error, 'cases.form.failed'), fields: {} };
}

function loadFailure(error: unknown): MessageKey {
  return isStatus(error, 404) ? 'cases.edit.notFound' : problemMessage(error, 'cases.edit.loadFailed');
}

function formValues(legalCase: LegalCase): NewCase {
  return {
    referenceNo: legalCase.referenceNo,
    title: legalCase.title,
    caseType: legalCase.caseType,
    subject: legalCase.subject ?? '',
    openedOn: legalCase.openedOn,
  };
}

/** LD-013: edit a case with the version it was loaded at; a stale version offers to reload. */
export function CaseEditPage() {
  const { locale, t } = useI18n();
  const { id = '' } = useParams();
  const created = (useLocation().state as CreatedState | null)?.created;
  const [generation, setGeneration] = useState(0);
  // The answer for one request key; any other key (a different case, or a reload) is still loading.
  const key = `${id}#${String(generation)}`;
  const [result, setResult] = useState<{ key: string; load: Load } | null>(null);
  const load: Load = result?.key === key ? result.load : { kind: 'loading' };
  const [saved, setSaved] = useState(false);

  usePageTitle(
    load.kind === 'loaded' ? t('cases.edit.title', { referenceNo: load.legalCase.referenceNo }) : t('cases.title'),
  );

  useEffect(() => {
    const controller = new AbortController();
    getCase(id, controller.signal).then(
      (legalCase) => {
        if (!controller.signal.aborted) setResult({ key, load: { kind: 'loaded', legalCase } });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ key, load: { kind: 'failed', message: loadFailure(error) } });
      },
    );
    return () => {
      controller.abort();
    };
  }, [id, key]);

  const back = (
    <p>
      <Link to={withLang(CASES_PATH, locale)}>{t('cases.form.back')}</Link>
    </p>
  );

  if (load.kind === 'loading') {
    return (
      <>
        <h1>{t('cases.title')}</h1>
        <p role="status">{t('cases.edit.loading')}</p>
      </>
    );
  }
  if (load.kind === 'failed') {
    return (
      <>
        <h1>{t('cases.title')}</h1>
        <p className="case-alert" role="alert">
          {t(load.message)}
        </p>
        {back}
      </>
    );
  }

  const legalCase = load.legalCase;
  const submit = async (values: NewCase) => {
    const updated = await updateCase(legalCase.id, values, legalCase.version);
    setResult({ key, load: { kind: 'loaded', legalCase: updated } });
    setSaved(true);
  };
  const reload = () => {
    setSaved(false);
    setGeneration((n) => n + 1);
  };

  return (
    <>
      <h1>{t('cases.edit.title', { referenceNo: legalCase.referenceNo })}</h1>
      {back}
      {saved || created !== undefined ? (
        <p className="case-notice" role="status">
          {saved ? t('cases.edit.saved') : t('cases.create.done', { referenceNo: created ?? '' })}
        </p>
      ) : null}
      <dl className="case-facts">
        <dt>{t('cases.field.status')}</dt>
        <dd>{t(`cases.status.${legalCase.status}`)}</dd>
      </dl>
      <CaseForm
        key={`${String(legalCase.version)}-${String(generation)}`}
        initial={formValues(legalCase)}
        withReference={false}
        submitLabel="cases.edit.submit"
        onSubmit={submit}
        explain={explain}
        onReload={reload}
      />
    </>
  );
}
