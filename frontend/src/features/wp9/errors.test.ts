import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/http';
import { failureKey, isForbidden } from './errors';

describe('what a failed request of the WP9 screens means', () => {
  it('maps each status to its own message and a refusing rule to its own text', () => {
    expect(failureKey(new ApiError(400, null))).toBe('wp9.error.fields');
    expect(failureKey(new ApiError(401, null))).toBe('wp9.error.signIn');
    expect(failureKey(new ApiError(403, null))).toBe('wp9.error.forbidden');
    expect(failureKey(new ApiError(404, null))).toBe('wp9.error.gone');
    expect(failureKey(new ApiError(409, null))).toBe('wp9.error.stale');
    expect(failureKey(new ApiError(422, { rule: 'SETTLEMENT_TRANSITION' }))).toBe('wp9.rule.SETTLEMENT_TRANSITION');
    expect(failureKey(new ApiError(422, { rule: 'NOT_A_RULE' }))).toBe('wp9.rule.unknown');
    expect(failureKey(new ApiError(422, null))).toBe('wp9.rule.unknown');
    expect(failureKey(new ApiError(500, null))).toBe('wp9.error.save');
    expect(failureKey(new Error('offline'))).toBe('wp9.error.save');
  });

  it('knows a refusal to open an area', () => {
    expect(isForbidden(new ApiError(403, null))).toBe(true);
    expect(isForbidden(new ApiError(404, null))).toBe(false);
    expect(isForbidden('x')).toBe(false);
  });
});
