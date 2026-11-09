import { useI18n } from '../../i18n/I18nContext';
import { listCaseDocuments } from '../operations/documents/documentsApi';
import { CLAIM_RESULTS, DEFENSE_POSITIONS, JUDGMENT_STATUSES, listClaims, listDefenses, listLookup } from './api';
import { BLANK, type DetailsState, type Extras } from './judgmentState';
import { documentOptions } from './pickers';
import { AreaField, ChoiceField, TextField } from './ui';
import { useLoad } from './useLoad';

/** Fields of the extended judgment plus a result selector for every registered claim and a position for every defence. */
interface Props {
  caseId: string;
  extras: Extras;
  /** The judgment's own summary, offered as the operative text when completing a judgment recorded before section 9. */
  summary: string | null;
}

export function JudgmentExtras({ caseId, extras, summary }: Props) {
  const { t, locale } = useI18n();
  const claims = useLoad((signal) => listClaims(caseId, signal), [caseId]);
  const defenses = useLoad((signal) => listDefenses(caseId, signal), [caseId]);
  const types = useLoad((signal) => listLookup('JUDGMENT_TYPE', false, signal), []);
  const natures = useLoad((signal) => listLookup('JUDGMENT_NATURE', false, signal), []);
  const documents = useLoad((signal) => listCaseDocuments(caseId, signal), [caseId]);
  const { details, setDetails } = extras;
  const set = (field: keyof DetailsState) => (value: string) => {
    setDetails({ ...details, [field]: value });
  };
  const lookup = (values: typeof types.data) =>
    (values ?? []).map((v) => [v.id, (locale === 'ar' ? v.labelAr : (v.labelEn ?? v.labelAr)) + (v.needsApproval ? ` (${t('pl.needsApproval')})` : '')] as const);

  return (
    <>
      <fieldset className="case-fieldset">
        <legend>{t('pl.judgment.details')}</legend>
        <TextField label={t('pl.judgment.chamber')} value={details.chamber} onChange={set('chamber')} max={100} />
        <ChoiceField
          label={t('pl.judgment.type')}
          value={details.typeId}
          onChange={set('typeId')}
          options={lookup(types.data)}
          empty={t('pl.notSpecified')}
        />
        <ChoiceField
          label={t('pl.judgment.nature')}
          value={details.natureId}
          onChange={set('natureId')}
          options={lookup(natures.data)}
          empty={t('pl.notSpecified')}
        />
        <AreaField label={t('pl.judgment.operative')} value={details.operative} onChange={set('operative')} />
        {summary !== null && details.operative === '' ? (
          <button
            type="button"
            onClick={() => {
              set('operative')(summary);
            }}
          >
            {t('pl.judgment.copySummary')}
          </button>
        ) : null}
        <AreaField label={t('pl.judgment.reasons')} value={details.reasons} onChange={set('reasons')} />
        <TextField label={t('pl.judgment.costs')} value={details.costs} onChange={set('costs')} dir="ltr" />
        <TextField label={t('pl.judgment.fees')} value={details.fees} onChange={set('fees')} dir="ltr" />
        <ChoiceField
          label={t('pl.judgment.appealable')}
          value={details.appealable}
          onChange={set('appealable')}
          options={[
            ['true', t('pl.yes')],
            ['false', t('pl.no')],
          ]}
          empty={t('pl.notSpecified')}
        />
        <TextField label={t('pl.judgment.notifiedOn')} type="date" value={details.notifiedOn} onChange={set('notifiedOn')} />
        <ChoiceField
          label={t('pl.judgment.status')}
          value={details.status}
          onChange={set('status')}
          options={JUDGMENT_STATUSES.map((s) => [s, t(`pl.judgment.status.${s}`)] as const)}
          empty={t('pl.notSpecified')}
        />
        <ChoiceField
          label={t('pl.judgment.copy')}
          value={details.copyDocumentId}
          onChange={set('copyDocumentId')}
          options={documentOptions(documents.data ?? [])}
          empty={t('pl.notSpecified')}
        />
      </fieldset>

      <fieldset className="case-fieldset">
        <legend>{t('pl.judgment.claimResults')}</legend>
        {(claims.data ?? []).length === 0 ? <p>{t('pl.judgment.noClaims')}</p> : null}
        {(claims.data ?? []).map((c) => {
          const entry = extras.claims[c.id] ?? BLANK;
          return (
            <div key={c.id} className="pleadings-row" role="group" aria-label={t('pl.judgment.claimNumber', { number: String(c.seq) })}>
              <p>
                <strong>{c.seq}.</strong> {c.text}
              </p>
              <ChoiceField
                label={t('pl.judgment.resultOf', { number: String(c.seq) })}
                value={entry.value}
                onChange={(value) => {
                  extras.setEntry('claims', c.id, { ...entry, value });
                }}
                options={CLAIM_RESULTS.map((r) => [r, t(`pl.claims.result.${r}`)] as const)}
                empty={t('pl.judgment.notRecordedYet')}
              />
              <TextField
                label={t('pl.judgment.amountOf', { number: String(c.seq) })}
                value={entry.amount}
                dir="ltr"
                onChange={(amount) => {
                  extras.setEntry('claims', c.id, { ...entry, amount });
                }}
              />
              <TextField
                label={t('pl.judgment.reasonsOf', { number: String(c.seq) })}
                value={entry.reasons}
                onChange={(reasons) => {
                  extras.setEntry('claims', c.id, { ...entry, reasons });
                }}
              />
            </div>
          );
        })}
      </fieldset>

      {(defenses.data ?? []).length === 0 ? null : (
        <fieldset className="case-fieldset">
          <legend>{t('pl.judgment.defensePositions')}</legend>
          {(defenses.data ?? []).map((d) => {
            const entry = extras.defenses[d.id] ?? BLANK;
            return (
              <div key={d.id} className="pleadings-row" role="group" aria-label={t('pl.judgment.defenseNumber', { number: String(d.seq) })}>
                <p>
                  <strong>{d.seq}.</strong> {d.title}
                </p>
                <ChoiceField
                  label={t('pl.judgment.positionOf', { number: String(d.seq) })}
                  value={entry.value}
                  onChange={(value) => {
                    extras.setEntry('defenses', d.id, { ...entry, value });
                  }}
                  options={DEFENSE_POSITIONS.map((p) => [p, t(`pl.defenses.position.${p}`)] as const)}
                  empty={t('pl.judgment.notRecordedYet')}
                />
                <TextField
                  label={t('pl.judgment.positionReasonsOf', { number: String(d.seq) })}
                  value={entry.reasons}
                  onChange={(reasons) => {
                    extras.setEntry('defenses', d.id, { ...entry, reasons });
                  }}
                />
              </div>
            );
          })}
        </fieldset>
      )}
    </>
  );
}
