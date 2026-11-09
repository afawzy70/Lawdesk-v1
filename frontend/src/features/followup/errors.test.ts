import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/http';
import { failureKey, isForbidden } from './errors';

describe('WP3 failures', () => {
  it('explains each kind of refusal', () => {
    expect(failureKey(new Error('x'))).toBe('wp3.error.save');
    expect(failureKey(new ApiError(400, null))).toBe('wp3.error.fields');
    expect(failureKey(new ApiError(401, null))).toBe('wp3.error.signIn');
    expect(failureKey(new ApiError(403, null))).toBe('wp3.error.forbidden');
    expect(failureKey(new ApiError(404, null))).toBe('wp3.error.gone');
    expect(failureKey(new ApiError(409, null))).toBe('wp3.error.stale');
    expect(failureKey(new ApiError(422, { rule: 'REMEDIATION_REQUIRED' }))).toBe('wp3.rule.REMEDIATION_REQUIRED');
    expect(failureKey(new ApiError(422, { rule: 'NOT_A_RULE' }))).toBe('wp3.rule.unknown');
    expect(failureKey(new ApiError(422, null))).toBe('wp3.rule.unknown');
    expect(failureKey(new ApiError(500, null))).toBe('wp3.error.save');
  });

  it('recognises a refusal to open an area', () => {
    expect(isForbidden(new ApiError(403, null))).toBe(true);
    expect(isForbidden(new ApiError(404, null))).toBe(false);
    expect(isForbidden(new Error('x'))).toBe(false);
  });
});
