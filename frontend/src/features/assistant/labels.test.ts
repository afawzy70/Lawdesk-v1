import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/http';
import { failureKey, keyFor } from './labels';

describe('assistant labels', () => {
  it('finds a catalog key or reports that there is none', () => {
    expect(keyFor('assistant.task', 'QUESTION')).toBe('assistant.task.QUESTION');
    expect(keyFor('assistant.task', 'NOPE')).toBeNull();
  });

  it('turns a failed request into words, whatever the server wrote', () => {
    expect(failureKey(new ApiError(400, null))).toBe('assistant.error.fields');
    expect(failureKey(new ApiError(403, null))).toBe('assistant.error.forbidden');
    expect(failureKey(new ApiError(404, null))).toBe('assistant.error.gone');
    expect(failureKey(new ApiError(500, null))).toBe('assistant.error.generic');
    expect(failureKey(new Error('x'))).toBe('assistant.error.generic');
  });
});
