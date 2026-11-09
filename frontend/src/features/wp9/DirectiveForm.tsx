import { type SyntheticEvent, useEffect, useId, useState } from 'react';
import { PagedSelect } from '../../components/PagedSelect';
import { useI18n } from '../../i18n/I18nContext';
import { searchCases, type CaseListItem } from '../cases/api';
import { todayIso } from '../cases/format';
import { listAssignees, type DirectoryUser } from '../operations/tasks/tasksApi';
import { valueLabel, type DirectiveInput, type ListValue } from './api';

interface Props {
  kinds: readonly ListValue[];
  onSubmit: (input: DirectiveInput) => Promise<boolean>;
}

const empty = (): DirectiveInput => ({
  caseId: '',
  kind: '',
  title: '',
  details: '',
  directiveDate: todayIso(),
  source: '',
  sourceReferenceNo: '',
  addresseeUserId: '',
  addresseeText: '',
  dueDate: '',
});

/** A new directive (section 25): the case, what is asked, who issued it, who it is addressed to and by when. */
export function DirectiveForm({ kinds, onSubmit }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [form, setForm] = useState<DirectiveInput>(empty);
  const [people, setPeople] = useState<DirectoryUser[]>([]);
  const [invalid, setInvalid] = useState(false);
  const set = <K extends keyof DirectiveInput>(field: K, value: DirectiveInput[K]) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  useEffect(() => {
    const controller = new AbortController();
    listAssignees(controller.signal).then(setPeople, () => {
      setPeople([]);
    });
    return () => {
      controller.abort();
    };
  }, []);

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    const missing =
      form.caseId === '' ||
      form.title.trim() === '' ||
      form.source.trim() === '' ||
      (form.addresseeUserId === '' && form.addresseeText.trim() === '') ||
      (form.dueDate !== '' && form.dueDate < form.directiveDate);
    if (missing) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    if (await onSubmit(form)) setForm(empty());
  };

  const input = (field: 'title' | 'source' | 'sourceReferenceNo' | 'addresseeText', label: string, max: number) => (
    <div className="case-field">
      <label htmlFor={`${id}-${field}`}>{label}</label>
      <input
        id={`${id}-${field}`}
        type="text"
        maxLength={max}
        value={form[field]}
        onChange={(e) => {
          set(field, e.target.value);
        }}
      />
    </div>
  );

  return (
    <form className="case-form" aria-label={t('wp9.directive.add')} noValidate onSubmit={(e) => void submit(e)}>
      {!invalid ? null : (
        <p className="case-alert" role="alert">
          {t('wp9.directive.invalid')}
        </p>
      )}
      <div className="case-field">
        <label htmlFor={`${id}-case`}>{t('wp9.directive.case')}</label>
        <PagedSelect<CaseListItem>
          selectProps={{ id: `${id}-case` }}
          value={form.caseId}
          onChange={(value) => {
            set('caseId', value);
          }}
          load={(page, query, signal) => searchCases({ q: query, page }, signal)}
          optionLabel={(c) => `${c.referenceNo} — ${c.title}`}
          serverSearch
          searchLabel={t('wp9.directive.searchCases')}
          chooseLabel={t('wp9.choose')}
        />
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-kind`}>{t('wp9.directive.kind')}</label>
        <select
          id={`${id}-kind`}
          value={form.kind}
          onChange={(e) => {
            set('kind', e.target.value);
          }}
        >
          <option value="">{t('wp9.notSpecified')}</option>
          {kinds.map((kind) => (
            <option key={kind.id} value={kind.code}>
              {valueLabel(kind, locale)}
            </option>
          ))}
        </select>
      </div>
      {input('title', t('wp9.directive.titleField'), 300)}
      <div className="case-field">
        <label htmlFor={`${id}-details`}>{t('wp9.directive.details')}</label>
        <textarea
          id={`${id}-details`}
          rows={3}
          maxLength={20000}
          value={form.details}
          onChange={(e) => {
            set('details', e.target.value);
          }}
        />
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-date`}>{t('wp9.directive.date')}</label>
        <input
          id={`${id}-date`}
          type="date"
          value={form.directiveDate}
          onChange={(e) => {
            set('directiveDate', e.target.value);
          }}
        />
      </div>
      {input('source', t('wp9.directive.source'), 300)}
      {input('sourceReferenceNo', t('wp9.directive.sourceRef'), 100)}
      <div className="case-field">
        <label htmlFor={`${id}-user`}>{t('wp9.directive.addressee')}</label>
        <select
          id={`${id}-user`}
          value={form.addresseeUserId}
          onChange={(e) => {
            set('addresseeUserId', e.target.value);
          }}
        >
          <option value="">{t('wp9.directive.addresseeNone')}</option>
          {people
            .filter((person) => person.enabled)
            .map((person) => (
              <option key={person.id} value={person.id}>
                {person.displayName}
              </option>
            ))}
        </select>
      </div>
      {input('addresseeText', t('wp9.directive.addresseeText'), 300)}
      <div className="case-field">
        <label htmlFor={`${id}-due`}>{t('wp9.directive.due')}</label>
        <input
          id={`${id}-due`}
          type="date"
          value={form.dueDate}
          onChange={(e) => {
            set('dueDate', e.target.value);
          }}
        />
      </div>
      <div className="case-actions">
        <button type="submit" className="primary">
          {t('wp9.directive.add')}
        </button>
      </div>
    </form>
  );
}
