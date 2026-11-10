import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ref } from '../library/testSupport';
import { DefenseSimilarJudgments } from './DefenseSimilarJudgments';
import { CASE, problem, renderWith, serve } from './testSupport';

/** LD-DOC-16-043: in the defence area a member picks one defence and sees the library judgments that resemble it. */

const SIMILAR = `/api/v1/cases/${CASE}/similar-judgments`;
const DEFENSES = [
  { id: 'd1', seq: 1, title: 'Prescription' },
  { id: 'd2', seq: 2, title: 'Late filing' },
];
const result = {
  caseId: CASE,
  basis: { terms: ['x'], caseType: 'LABOR', courtLevels: [], defensesIncluded: true, claimsIncluded: false, defenseId: 'd1' },
  items: [
    {
      judgment: ref({ id: 'j9', subject: 'تقادم الدعوى' }),
      score: 40,
      reasons: [{ code: 'TEXT_MATCH', points: 20, terms: ['تقادم'] }],
      differences: [],
      relationTypes: [],
      warnings: [],
      alreadyLinked: false,
    },
  ],
  note: 'n',
};

describe('similar judgments of one defence', () => {
  it('asks the API for the chosen defence alone and shows each judgment with its reasons', async () => {
    const backend = serve({ [`GET ${SIMILAR}`]: result });
    renderWith(<DefenseSimilarJudgments caseId={CASE} defenses={DEFENSES} />);
    expect(screen.getByRole('button', { name: 'Show similar judgments' })).toBeDisabled();
    await userEvent.selectOptions(screen.getByLabelText('Defence'), 'd2');
    await userEvent.click(screen.getByRole('button', { name: 'Show similar judgments' }));
    expect(await screen.findByRole('link', { name: 'تقادم الدعوى' })).toHaveAttribute('href', '/library/j9?lang=en');
    expect(screen.getByText(/Shared words: تقادم \(20 points\)/)).toBeVisible();
    expect(screen.getByText('Score 40')).toBeVisible();
    expect(backend.to('GET', SIMILAR)[0]?.query).toBe('?defenseId=d2');
  });

  it('says plainly when nothing is similar and when the comparison is refused or fails', async () => {
    serve({ [`GET ${SIMILAR}`]: { ...result, items: [] } });
    const first = renderWith(<DefenseSimilarJudgments caseId={CASE} defenses={DEFENSES} />);
    await userEvent.selectOptions(screen.getByLabelText('Defence'), 'd1');
    await userEvent.click(screen.getByRole('button', { name: 'Show similar judgments' }));
    expect(await screen.findByText('No similar judgment found for this defence.')).toBeVisible();
    first.unmount();

    serve({ [`GET ${SIMILAR}`]: problem(403) });
    const second = renderWith(<DefenseSimilarJudgments caseId={CASE} defenses={DEFENSES} />);
    await userEvent.selectOptions(screen.getByLabelText('Defence'), 'd1');
    await userEvent.click(screen.getByRole('button', { name: 'Show similar judgments' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('You may not use defences for this comparison.');
    second.unmount();

    serve({ [`GET ${SIMILAR}`]: problem(500) });
    renderWith(<DefenseSimilarJudgments caseId={CASE} defenses={DEFENSES} />);
    await userEvent.selectOptions(screen.getByLabelText('Defence'), 'd1');
    await userEvent.click(screen.getByRole('button', { name: 'Show similar judgments' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
  });

  it('is absent without the library or defence permission, calls nothing, and is in Arabic on request', () => {
    const backend = serve({ [`GET ${SIMILAR}`]: result });
    const without = renderWith(<DefenseSimilarJudgments caseId={CASE} defenses={DEFENSES} />, { permissions: ['CASE_VIEW', 'DEFENSE_VIEW'] });
    expect(without.container).toBeEmptyDOMElement();
    without.unmount();
    const noDefences = renderWith(<DefenseSimilarJudgments caseId={CASE} defenses={[]} />);
    expect(noDefences.container).toBeEmptyDOMElement();
    noDefences.unmount();
    expect(backend.sent).toHaveLength(0);
    renderWith(<DefenseSimilarJudgments caseId={CASE} defenses={DEFENSES} />, { lang: 'ar' });
    expect(screen.getByRole('heading', { name: 'أحكام مشابهة لدفع' })).toBeVisible();
  });
});
