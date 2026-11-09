import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../cases/format';
import type { Answer, Block, Field, Source } from './api';
import { keyFor } from './labels';
import './assistant.css';

function FieldList({ fields }: { fields: readonly Field[] }) {
  const { t } = useI18n();
  if (fields.length === 0) return null;
  return (
    <dl className="assistant-fields">
      {fields.map((field, index) => {
        const label = keyFor('assistant.field', field.code);
        const value = keyFor('assistant.value', field.value);
        return (
          <div key={`${field.code}-${String(index)}`}>
            <dt>{label === null ? field.code : t(label)}</dt>
            <dd dir="auto">{value === null ? field.value : t(value)}</dd>
          </div>
        );
      })}
    </dl>
  );
}

function Quote({ block, suspected }: { block: Block; suspected: boolean }) {
  const { t } = useI18n();
  return (
    <blockquote className="assistant-quote">
      <p className="assistant-kind">{t('assistant.answer.stored')}</p>
      {block.text !== null && <p dir="auto">{block.text}</p>}
      {block.fields !== null && <FieldList fields={block.fields} />}
      <p className="assistant-cites">
        {t('assistant.answer.cites', { list: block.sources.join('، ') })}
        {block.sources.map((n) => (
          <a key={n} href={`#assistant-source-${String(n)}`} className="assistant-cite">
            [{n}]
          </a>
        ))}
      </p>
      {suspected && (
        <p role="note" className="case-notice">
          {t('assistant.answer.instruction')}
        </p>
      )}
    </blockquote>
  );
}

function SourceItem({ source }: { source: Source }) {
  const { t, locale } = useI18n();
  const type = keyFor('assistant.type', source.type);
  return (
    <li id={`assistant-source-${String(source.n)}`} className="assistant-source">
      <strong>
        [{source.n}] {type === null ? source.type : t(type)}
      </strong>
      {source.title !== null && <span dir="auto"> — {source.title}</span>}
      {source.caseReferenceNo !== null && <span> · {source.caseReferenceNo}</span>}
      {source.date !== null && <span> · {formatDate(source.date, locale)}</span>}
      <small className="assistant-page"> · {source.page === null ? t('assistant.answer.noPage') : String(source.page)}</small>
      <FieldList fields={source.fields} />
      {source.suspectedInstruction && (
        <p role="note" className="case-notice">
          {t('assistant.answer.instruction')}
        </p>
      )}
    </li>
  );
}

/**
 * An answer of the assistant (section 22.7): stored text is shown as a quotation with the numbers of its sources and is told
 * apart from the assistant's own fixed remarks; every source is listed; a refusal says that nothing was invented. Text from
 * records is shown as plain text only.
 */
export function AnswerView({ answer }: { answer: Answer }) {
  const { t } = useI18n();
  const suspected = new Set(answer.sources.filter((s) => s.suspectedInstruction).map((s) => s.n));
  const refusal = answer.refusal === null ? null : keyFor('assistant.refusal', answer.refusal);
  return (
    <section className="assistant-answer" aria-label={t('assistant.answer.title')}>
      <h3>{t('assistant.answer.title')}</h3>
      {answer.outcome === 'INSUFFICIENT_SOURCES' ? (
        <div role="status" className="case-alert">
          <p>{t('assistant.answer.refused')}</p>
          {refusal !== null && <p>{t(refusal)}</p>}
        </div>
      ) : (
        <>
          {answer.blocks.map((block, index) => {
            const key = `${block.kind}-${String(index)}`;
            if (block.kind === 'HEADING') {
              const heading = block.code === null ? null : keyFor('assistant.heading', block.code);
              return <h4 key={key}>{heading === null ? block.code : t(heading)}</h4>;
            }
            if (block.kind === 'NOTE') {
              const note = block.code === null ? null : keyFor('assistant.note', block.code);
              return (
                <p key={key} className="assistant-note">
                  <span className="assistant-kind">{t('assistant.answer.note')}: </span>
                  {note === null ? block.code : t(note)}
                </p>
              );
            }
            return <Quote key={key} block={block} suspected={block.sources.some((n) => suspected.has(n))} />;
          })}
          <h4>{t('assistant.answer.sources')}</h4>
          <ol className="assistant-sources">
            {answer.sources.map((source) => (
              <SourceItem key={source.n} source={source} />
            ))}
          </ol>
        </>
      )}
      <p className="case-notice">{t('assistant.answer.notFinal')}</p>
      {answer.outputId !== null && <p role="status">{t('assistant.answer.draftKept')}</p>}
    </section>
  );
}
