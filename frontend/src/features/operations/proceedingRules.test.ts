import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/http';
import { translate } from '../../i18n/catalog';
import { saveError } from '../proceedings/formErrors';

/**
 * QAD-032: correcting a proceeding's filing date to after one of its Phase 5 records is refused with a rule the
 * proceeding forms explain in both languages (the proceedings screens map `proceedings.rule.<RULE>`).
 */
describe('proceeding rules raised by Phase 5 records', () => {
  it('explains a filing date that would follow a hearing', () => {
    const key = saveError(new ApiError(422, { rule: 'FILING_AFTER_DEPENDENT_HEARING' }), 'proceedings.error.save');
    expect(key).toBe('proceedings.rule.FILING_AFTER_DEPENDENT_HEARING');
    expect(translate('en', key)).toBe('The filing date cannot be after a hearing of this proceeding.');
    expect(translate('ar', key)).toBe('لا يجوز أن يكون تاريخ القيد بعد جلسة من جلسات هذه الدعوى.');
  });
});
