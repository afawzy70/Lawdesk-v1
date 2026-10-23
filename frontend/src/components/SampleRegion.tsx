import { useId, type ReactNode } from 'react';
import { useI18n } from '../i18n/I18nContext';

interface Props {
  title: string;
  className?: string;
  children: ReactNode;
}

/**
 * A dashboard region that shows mock data. It is a named region whose accessible description and visible badge
 * both say "Sample data", so the label cannot be missed by sighted or screen-reader users.
 */
export function SampleRegion({ title, className, children }: Props) {
  const { t } = useI18n();
  const headingId = useId();
  const badgeId = useId();
  return (
    <section
      className={className === undefined ? 'sample-region' : `sample-region ${className}`}
      aria-labelledby={headingId}
      aria-describedby={badgeId}
    >
      <div className="sample-region-head">
        <h2 id={headingId}>{title}</h2>
        <p id={badgeId} className="sample-badge">
          {t('preview.sampleData')}
        </p>
      </div>
      {children}
    </section>
  );
}
