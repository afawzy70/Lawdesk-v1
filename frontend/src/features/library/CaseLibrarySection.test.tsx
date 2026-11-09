import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LocaleProvider } from '../../i18n/LocaleProvider';
import { AuthContext } from '../auth/AuthContext';
import type { Me } from '../auth/me';
import type { Precedent, Suggestion, Suggestions } from './api';
import { CaseLibrarySection } from './CaseLibrarySection';
import { jsonResponse, problem, ref, script, user } from './testSupport';

/** LD-052, acceptance scenario S5: the case page offers similar judgments, explains them, and the member decides. */
beforeEach(() => {
  vi.unstubAllGlobals();
});

const suggestion = (overrides: Partial<Suggestion> = {}): Suggestion => ({
  judgment: ref(),
  score: 55,
  reasons: [
    { code: 'TEXT_MATCH', points: 20, terms: ['تعويض', 'فصل'] },
    { code: 'SAME_CASE_TYPE', points: 20, terms: ['LABOR'] },
    { code: 'SUBJECT_SIMILARITY', points: 15, terms: ['0.30'] },
  ],
  differences: [
    { code: 'DIFFERENT_COURT_LEVEL', detail: 'APPEAL' },
    { code: 'DIFFERENT_CASE_TYPE', detail: 'CIVIL' },
  ],
  relationTypes: [],
  warnings: [],
  alreadyLinked: false,
  ...overrides,
});

const suggestions = (items: Suggestion[], defenses = false): Suggestions => ({
  caseId: 'c1',
  basis: { terms: ['تعويض'], caseType: 'LABOR', courtLevels: [], defensesIncluded: defenses },
  items,
  note: 'n',
});

const precedent = (overrides: Partial<Precedent> = {}): Precedent => ({
  id: 'l1',
  caseId: 'c1',
  judgment: ref({ id: 'j7', subject: 'حكم مرتبط', caseNumber: null, caseYear: null, judgmentDate: null }),
  reason: 'نفس مسألة التقادم',
  similarityNote: 'الوقائع متقاربة',
  origin: 'MANUAL',
  relationTypes: ['OVERTURNED'],
  ...overrides,
});

function mount(me: Me | null, editable = true) {
  return render(
    <MemoryRouter>
      <LocaleProvider>
        <AuthContext.Provider
          value={{
            state: me === null ? { status: 'signedOut', expired: false } : { status: 'signedIn', me },
            signIn: () => Promise.resolve(true),
            signOut: () => Promise.resolve(),
            refresh: () => Promise.resolve(),
          }}
        >
          <CaseLibrarySection caseId="c1" editable={editable} />
        </AuthContext.Provider>
      </LocaleProvider>
    </MemoryRouter>,
  );
}

describe('library on the case page', () => {
  it('renders nothing and calls nothing for a user without the library permission', () => {
    const { calls } = script(() => undefined);
    const view = mount(user(['CASE_VIEW']));
    expect(view.container).toBeEmptyDOMElement();
    expect(calls).toHaveLength(0);
  });

  it('renders nothing when nobody is signed in', () => {
    const { calls } = script(() => undefined);
    const view = mount(null);
    expect(view.container).toBeEmptyDOMElement();
    expect(calls).toHaveLength(0);
  });

  it('shows linked judgments and the suggestions with every reason, difference and warning', async () => {
    script((call) =>
      call.path.endsWith('/precedents')
        ? jsonResponse([precedent()])
        : jsonResponse(
            suggestions([
              suggestion({ warnings: ['LATER_RELATION_EXISTS'], relationTypes: ['OVERTURNED'] }),
              suggestion({ judgment: ref({ id: 'j3', subject: 'مرتبط بالفعل', hasCopy: false, caseNumber: null, caseYear: null, judgmentDate: null }), alreadyLinked: true, differences: [] }),
            ]),
          ),
    );
    mount(user());
    expect(await screen.findByRole('link', { name: 'التعويض عن إنهاء الخدمة' })).toHaveAttribute('href', '/library/j1?lang=en');
    expect(screen.getByRole('link', { name: 'حكم مرتبط' })).toBeInTheDocument();
    expect(screen.getByText('نفس مسألة التقادم')).toBeInTheDocument();
    expect(screen.getByText(/Later events recorded: Overturned/)).toBeInTheDocument();
    expect(screen.getAllByText(/Shared words: تعويض, فصل \(20 points\)/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Same type of dispute: LABOR \(20 points\)/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Similar subject wording \(15 points\)/).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Different court level: Appeal').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Different type of dispute: Civil').length).toBeGreaterThan(0);
    expect(screen.getByText('A later event is recorded for this judgment: check the sources below.')).toBeInTheDocument();
    expect(screen.getByText(/A copy is saved/)).toBeInTheDocument();
    expect(screen.getByText('Already linked')).toBeInTheDocument();
    expect(screen.getByText(/not a legal opinion/)).toBeInTheDocument();
    expect(screen.getByText(/defences are not used for the comparison yet/)).toBeInTheDocument();
  });

  it('says so when nothing is linked or similar, and omits the defences note once they are used', async () => {
    script((call) => (call.path.endsWith('/precedents') ? jsonResponse([]) : jsonResponse(suggestions([], true))));
    mount(user());
    expect(await screen.findByText('No judgment is linked to this case yet.')).toBeInTheDocument();
    expect(await screen.findByText('No similar judgment found in the library.')).toBeInTheDocument();
    expect(screen.queryByText(/defences are not used/)).not.toBeInTheDocument();
  });

  it('links a suggestion only after the member gives the reason', async () => {
    let linked: Precedent[] = [];
    const { calls } = script((call) => {
      if (call.method === 'POST') {
        linked = [precedent()];
        return jsonResponse(precedent(), 201);
      }
      return call.path.endsWith('/precedents') ? jsonResponse(linked) : jsonResponse(suggestions([suggestion()]));
    });
    const typing = userEvent.setup();
    mount(user());
    await typing.click(await screen.findByRole('button', { name: 'Link to this case' }));
    await typing.click(screen.getByRole('button', { name: 'Link' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Write why this judgment helps.');
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
    await typing.type(screen.getByLabelText('Why it helps in this case'), 'نفس مسألة التقادم');
    await typing.type(screen.getByLabelText('How alike are the facts and legal issues'), 'الوقائع متقاربة');
    await typing.click(screen.getByRole('button', { name: 'Link' }));
    expect(await screen.findByText('Judgment linked.')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({
      judgmentId: 'j1',
      reason: 'نفس مسألة التقادم',
      similarityNote: 'الوقائع متقاربة',
      origin: 'SUGGESTION',
    });
    expect(await screen.findByText('الوقائع متقاربة')).toBeInTheDocument();
  });

  it('links without a similarity note', async () => {
    const { calls } = script((call) => {
      if (call.method === 'POST') return jsonResponse(precedent(), 201);
      return call.path.endsWith('/precedents') ? jsonResponse([]) : jsonResponse(suggestions([suggestion()]));
    });
    const typing = userEvent.setup();
    mount(user());
    await typing.click(await screen.findByRole('button', { name: 'Link to this case' }));
    await typing.type(screen.getByLabelText('Why it helps in this case'), 'سبب');
    await typing.click(screen.getByRole('button', { name: 'Link' }));
    await waitFor(() => {
      expect(calls.find((c) => c.method === 'POST')?.body).toMatchObject({ similarityNote: null });
    });
  });

  it('unlinks a judgment', async () => {
    let linked = [precedent()];
    script((call) => {
      if (call.method === 'DELETE') {
        linked = [];
        return new Response(null, { status: 204 });
      }
      return call.path.endsWith('/precedents') ? jsonResponse(linked) : jsonResponse(suggestions([]));
    });
    const typing = userEvent.setup();
    mount(user());
    await typing.click(await screen.findByRole('button', { name: 'Unlink' }));
    expect(await screen.findByText('Judgment unlinked.')).toBeInTheDocument();
    expect(await screen.findByText('No judgment is linked to this case yet.')).toBeInTheDocument();
  });

  it('explains a refusal to a member who may see the case but not change it, and a conflict, and a failure', async () => {
    let status = 403;
    script((call) => {
      if (call.method !== 'GET') return problem(status);
      return call.path.endsWith('/precedents') ? jsonResponse([precedent()]) : jsonResponse(suggestions([suggestion()]));
    });
    const typing = userEvent.setup();
    mount(user());
    await typing.click(await screen.findByRole('button', { name: 'Unlink' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('may not change it');
    status = 409;
    await typing.click(screen.getByRole('button', { name: 'Unlink' }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Someone changed this record meanwhile');
    });
    status = 500;
    await typing.click(screen.getByRole('button', { name: 'Link to this case' }));
    await typing.type(screen.getByLabelText('Why it helps in this case'), 'سبب');
    await typing.click(screen.getByRole('button', { name: 'Link' }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('could not be loaded or changed');
    });
  });

  it('is read-only on an archived case', async () => {
    script((call) => (call.path.endsWith('/precedents') ? jsonResponse([precedent()]) : jsonResponse(suggestions([suggestion()]))));
    mount(user(), false);
    await screen.findByText('نفس مسألة التقادم');
    expect(screen.queryByRole('button', { name: 'Unlink' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Link to this case' })).not.toBeInTheDocument();
  });

  it('reports a failed load', async () => {
    script(() => problem(500));
    mount(user());
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded or changed');
  });
});
