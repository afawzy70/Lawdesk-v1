import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/http';
import { blankToNull, fieldMessage, loadError, saveError } from './formErrors';

/** How the operations forms explain backend answers (architecture/CONTRACTS.md §4). */
describe('operations form errors', () => {
  it('names each constraint the backend reports', () => {
    expect(fieldMessage('NotBlank')).toBe('operations.fieldError.required');
    expect(fieldMessage('NotNull')).toBe('operations.fieldError.required');
    expect(fieldMessage('Size')).toBe('operations.fieldError.tooLong');
    expect(fieldMessage('Range')).toBe('operations.fieldError.range');
    expect(fieldMessage('Null')).toBe('operations.fieldError.notAllowed');
    expect(fieldMessage('Pattern')).toBe('operations.fieldError.invalid');
  });

  it('explains refused saves by status and rule', () => {
    const conflict = 'operations.error.stale';
    expect(saveError(new Error('network'), conflict)).toBe('operations.error.save');
    expect(saveError(new ApiError(0, null), conflict)).toBe('operations.error.save');
    expect(saveError(new ApiError(401, null), conflict)).toBe('operations.error.signIn');
    expect(saveError(new ApiError(409, null), conflict)).toBe(conflict);
    expect(saveError(new ApiError(422, { rule: 'HEARING_NOT_YET_HELD' }), conflict)).toBe(
      'operations.rule.HEARING_NOT_YET_HELD',
    );
    expect(saveError(new ApiError(422, null), conflict)).toBe('operations.rule.unknown');
  });

  it('explains failed loads', () => {
    expect(loadError(new ApiError(403, null))).toBe('operations.error.viewForbidden');
    expect(loadError(new ApiError(404, null))).toBe('operations.error.caseNotFound');
    expect(loadError(new ApiError(401, null))).toBe('operations.error.signIn');
    expect(loadError(new ApiError(500, null))).toBe('operations.error.load');
    expect(loadError(new TypeError('offline'))).toBe('operations.error.load');
  });

  it('sends blank text as null and trims the rest', () => {
    expect(blankToNull('   ')).toBeNull();
    expect(blankToNull(' a ')).toBe('a');
  });
});
