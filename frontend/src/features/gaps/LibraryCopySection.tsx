import { type ChangeEvent, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { useLoad } from '../pleadings/useLoad';
import { copyUrl, getCopyInfo, removeCopy, storeCopy } from './api';

/** The electronic copy of a library judgment, kept in the archive (requirements section 16): download, replace, take out. */
export function LibraryCopySection({ judgmentId, canManage }: { judgmentId: string; canManage: boolean }) {
  const { t } = useI18n();
  const info = useLoad((signal) => getCopyInfo(judgmentId, signal), [judgmentId]);
  const [failed, setFailed] = useState(false);
  const copy = info.status === null ? info.data : null;

  const upload = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file === undefined) return;
    storeCopy(judgmentId, file).then(
      () => { setFailed(false); info.reload(); },
      () => { setFailed(true); },
    );
  };
  const take = () => {
    removeCopy(judgmentId).then(
      () => { setFailed(false); info.reload(); },
      () => { setFailed(true); },
    );
  };

  return (
    <section className="library-section" aria-label={t('gap.copy.title')}>
      <h2>{t('gap.copy.title')}</h2>
      {copy === null ? (
        <p>{t('gap.copy.none')}</p>
      ) : (
        <p>
          <a href={copyUrl(judgmentId)}>{copy.fileName}</a> ({String(copy.sizeBytes)} B)
          {canManage ? (
            <>
              {' '}
              <button type="button" onClick={take}>{t('gap.copy.remove')}</button>
            </>
          ) : null}
        </p>
      )}
      {canManage ? (
        <p>
          <label>
            {t(copy === null ? 'gap.copy.upload' : 'gap.copy.replace')} <input type="file" onChange={upload} />
          </label>
        </p>
      ) : null}
      {failed ? <p role="alert">{t('gap.failed')}</p> : null}
    </section>
  );
}
