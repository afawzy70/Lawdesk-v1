import { useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../../i18n/catalog';
import { formatDateTime } from '../../../i18n/format';
import { useI18n } from '../../../i18n/I18nContext';
import { loadError, saveError, useFormErrors } from '../formErrors';
import { Field } from '../FormParts';
import { addNote, editNote, listNotes, removeNote, type CaseNote } from './notesApi';

type Result = { key: number; notes: CaseNote[] } | { key: number; error: MessageKey };

interface NoteFormProps {
  label: string;
  initial: string;
  submitLabel: string;
  save: (body: string) => Promise<unknown>;
  onSaved: () => void;
  onCancel?: (() => void) | undefined;
}

/** The note's text and its submit; the backend checks it is present and at most 4000 characters. */
function NoteForm({ label, initial, submitLabel, save, onSaved, onCancel }: NoteFormProps) {
  const { t } = useI18n();
  const form = useFormErrors<'body'>();
  const [body, setBody] = useState(initial);
  const [saving, setSaving] = useState(false);
  const submit = async () => {
    setSaving(true);
    try {
      await save(body);
      setBody('');
      onSaved();
    } catch (error) {
      form.fail(error, 'operations.error.stale', ['body']);
    }
    setSaving(false);
  };
  return (
    <form
      className="ops-form"
      aria-label={label}
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      {form.formError === null ? null : <p className="ops-alert">{t(form.formError)}</p>}
      <Field id={form.control('body').id} label={t('operations.note.body')} error={form.errors.body}
        errorId={form.errorId('body')}>
        <textarea
          {...form.control('body')}
          rows={4}
          maxLength={4000}
          value={body}
          onChange={(event) => {
            setBody(event.target.value);
          }}
        />
      </Field>
      <div className="ops-form-actions">
        <button type="submit" disabled={saving}>
          {saving ? t('operations.saving') : submitLabel}
        </button>
        {onCancel === undefined ? null : (
          <button type="button" onClick={onCancel}>
            {t('operations.cancel')}
          </button>
        )}
      </div>
    </form>
  );
}

interface Props {
  caseId: string;
}

/**
 * The legal notes of one case (LD-029), newest first: written with NOTE_MANAGE, read with CASE_VIEW, changed and
 * removed by their author only. The case history (the timeline) records who added or removed one, never its text.
 */
export function CaseNotes({ caseId }: Props) {
  const { locale, t } = useI18n();
  const headingId = useId();
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [confirming, setConfirming] = useState<string | null>(null);
  const [notice, setNotice] = useState<'saved' | 'removed' | MessageKey | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    listNotes(caseId, controller.signal).then(
      (notes) => {
        setResult({ key: reload, notes });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ key: reload, error: loadError(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, reload]);

  const current = result?.key === reload ? result : null;
  const changed = (what: 'saved' | 'removed') => {
    setEditing(null);
    setConfirming(null);
    setNotice(what);
    setReload((n) => n + 1);
  };
  const remove = async (note: CaseNote) => {
    setNotice(null);
    try {
      await removeNote(note);
      changed('removed');
    } catch (error) {
      setConfirming(null);
      setNotice(saveError(error, 'operations.error.stale'));
    }
  };

  const item = (note: CaseNote) => (
    <li key={note.id}>
      <article className="ops-item" aria-label={t('operations.note.label', { name: note.authorName })}>
        {editing === note.id ? (
          <NoteForm
            label={t('operations.note.editTitle')}
            initial={note.body}
            submitLabel={t('operations.save')}
            save={(body) => editNote(note, body)}
            onSaved={() => {
              changed('saved');
            }}
            onCancel={() => {
              setEditing(null);
            }}
          />
        ) : (
          <p className="ops-wrap ops-note">{note.body}</p>
        )}
        <p className="ops-hint">
          {t('operations.note.byline', { name: note.authorName, date: formatDateTime(note.createdAt, locale) })}
          {note.edited ? ` ${t('operations.note.edited')}` : null}
        </p>
        {note.editable && editing !== note.id ? (
          <div className="ops-actions">
            <button
              type="button"
              onClick={() => {
                setNotice(null);
                setConfirming(null);
                setEditing(note.id);
              }}
            >
              {t('operations.edit')}
            </button>
            <button
              type="button"
              onClick={() => {
                setNotice(null);
                setConfirming(note.id);
              }}
            >
              {t('operations.document.remove')}
            </button>
          </div>
        ) : null}
        {confirming === note.id ? (
          <div className="ops-confirm">
            <p>{t('operations.note.confirmRemove')}</p>
            <div className="ops-actions">
              <button
                type="button"
                onClick={() => {
                  void remove(note);
                }}
              >
                {t('operations.note.removeButton')}
              </button>
              <button
                type="button"
                onClick={() => {
                  setConfirming(null);
                }}
              >
                {t('operations.document.keep')}
              </button>
            </div>
          </div>
        ) : null}
      </article>
    </li>
  );

  const body = () => {
    if (current === null) return <p>{t('operations.loading')}</p>;
    if ('error' in current) {
      return (
        // Not role="alert": this section loads with the case page, whose own alerts must stay the only ones announced.
        <div className="ops-alert ops-load-error">
          <p>{t(current.error === 'operations.error.viewForbidden' ? 'operations.note.hidden' : current.error)}</p>
          {current.error === 'operations.error.load' ? (
            <button
              type="button"
              onClick={() => {
                setReload((n) => n + 1);
              }}
            >
              {t('operations.retry')}
            </button>
          ) : null}
        </div>
      );
    }
    return current.notes.length === 0 ? (
      <p>{t('operations.note.none')}</p>
    ) : (
      <ul className="ops-list">{current.notes.map(item)}</ul>
    );
  };

  return (
    <section className="ops-section" aria-labelledby={headingId}>
      <h3 id={headingId}>{t('operations.notes.caseTitle')}</h3>
      <p className="ops-hint">{t('operations.notes.privacy')}</p>
      {notice === 'saved' || notice === 'removed' ? (
        <p role="status" className="ops-saved">
          {t(notice === 'saved' ? 'operations.note.saved' : 'operations.note.removed')}
        </p>
      ) : null}
      {notice !== null && notice !== 'saved' && notice !== 'removed' ? (
        <p className="ops-alert">{t(notice)}</p>
      ) : null}
      {/* Kept mounted while the list reloads, so the writer keeps the form they just used. */}
      {result === null || 'error' in result ? null : (
        <NoteForm
          label={t('operations.note.newTitle')}
          initial=""
          submitLabel={t('operations.note.add')}
          save={(text) => addNote(caseId, text)}
          onSaved={() => {
            changed('saved');
          }}
        />
      )}
      {body()}
    </section>
  );
}
