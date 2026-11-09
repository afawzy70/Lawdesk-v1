import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/http';
import { archiveError, statusOf } from './errors';

describe('archive errors', () => {
  it('explains each kind of refusal', () => {
    expect(archiveError(new Error('x'))).toBe('wp5.error.save');
    expect(archiveError(new ApiError(400, { errors: [{ field: 'file', code: 'UnsupportedFormat' }] }))).toBe('wp5.error.format');
    expect(archiveError(new ApiError(400, { errors: [{ field: 'file', code: 'MaxSize' }] }))).toBe('wp5.error.tooLarge');
    expect(archiveError(new ApiError(400, { errors: [{ field: 'title', code: 'NotBlank' }] }))).toBe('wp5.error.fields');
    expect(archiveError(new ApiError(400, null))).toBe('wp5.error.fields');
    expect(archiveError(new ApiError(401, null))).toBe('wp5.error.signIn');
    expect(archiveError(new ApiError(403, null))).toBe('wp5.error.forbidden');
    expect(archiveError(new ApiError(404, null))).toBe('wp5.error.gone');
    expect(archiveError(new ApiError(409, null))).toBe('wp5.error.stale');
    expect(archiveError(new ApiError(413, null))).toBe('wp5.error.tooLarge');
    expect(archiveError(new ApiError(500, null))).toBe('wp5.error.save');
  });

  it('names the rule of a 422 when the catalogue knows it', () => {
    expect(archiveError(new ApiError(422, { rule: 'DOCUMENT_REFERENCED' }))).toBe('wp5.rule.DOCUMENT_REFERENCED');
    expect(archiveError(new ApiError(422, { rule: 'SOMETHING_NEW' }))).toBe('wp5.rule.unknown');
    expect(archiveError(new ApiError(422, null))).toBe('wp5.rule.unknown');
  });

  it('reads the status of a failed load', () => {
    expect(statusOf(new ApiError(403, null))).toBe(403);
    expect(statusOf(new Error('offline'))).toBe(0);
  });
});
