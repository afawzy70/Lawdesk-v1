import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { createCase, type NewCase } from './api';
import { CaseForm, type Failure } from './CaseForm';
import { todayIso } from './format';
import { CASES_PATH, editCasePath } from './paths';
import { fieldErrors, isStatus, problemMessage } from './problems';

/** What the edit page shows after a creation (router state, so a reload does not repeat it). */
export interface CreatedState {
  created: string;
}

function explain(error: unknown): Failure {
  const fields = fieldErrors(error);
  if (isStatus(error, 409)) {
    return { message: 'cases.form.invalid', fields: { referenceNo: 'cases.error.duplicateReference' } };
  }
  if (isStatus(error, 422, 'REFERENCE_NO_RESERVED')) {
    return { message: 'cases.form.invalid', fields: { referenceNo: 'cases.error.reservedReference' } };
  }
  if (isStatus(error, 400)) {
    return { message: 'cases.form.invalid', fields };
  }
  return { message: problemMessage(error, 'cases.form.failed'), fields };
}

/** LD-012: create a case; on success the new case opens with a confirmation. */
export function CaseCreatePage() {
  const { locale, t } = useI18n();
  const navigate = useNavigate();
  usePageTitle(t('cases.create.title'));
  const [initial] = useState<NewCase>(() => ({
    referenceNo: '',
    title: '',
    caseType: '',
    subject: '',
    openedOn: todayIso(),
  }));

  const submit = async (values: NewCase) => {
    const created = await createCase(values);
    const state: CreatedState = { created: created.referenceNo };
    await navigate(withLang(editCasePath(created.id), locale), { state });
  };

  return (
    <>
      <h1>{t('cases.create.title')}</h1>
      <p>
        <Link to={withLang(CASES_PATH, locale)}>{t('cases.form.back')}</Link>
      </p>
      <CaseForm
        initial={initial}
        withReference
        submitLabel="cases.create.submit"
        onSubmit={submit}
        explain={explain}
      />
    </>
  );
}
