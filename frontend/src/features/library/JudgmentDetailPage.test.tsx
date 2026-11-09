import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Principle, Relation } from './api';
import { classification, jsonResponse, judgment, listItem, page, problem, renderLibrary, script, user } from './testSupport';

/** LD-052: one judgment with its principles, what happened to it and the cases that use it. */
beforeEach(() => {
  vi.unstubAllGlobals();
});

const principle = (overrides: Partial<Principle> = {}): Principle => ({
  id: 'p1',
  judgmentId: 'j1',
  principleText: 'الإنهاء بغير سبب مشروع يوجب التعويض',
  legalTopic: 'الفصل التعسفي',
  legalBasis: 'المادة 41',
  factsApplied: 'فصل موظف',
  scopeNote: 'العقود غير محددة المدة',
  memberNotes: 'يراجع مع الحكم',
  reviewStatus: 'DRAFT',
  reviewedBy: null,
  reviewedAt: null,
  version: 0,
  courtName: 'محكمة التمييز',
  caseNumber: '1234',
  caseYear: 2020,
  judgmentDate: '2021-03-02',
  ...overrides,
});

const relation = (overrides: Partial<Relation> = {}): Relation => ({
  id: 'r1',
  direction: 'OUTGOING',
  relationType: 'OVERTURNED',
  judgmentId: 'j1',
  otherJudgmentId: 'j2',
  otherSubject: 'حكم لاحق',
  otherCourtName: 'محكمة النقض',
  otherCaseNumber: '9',
  otherCaseYear: 2022,
  otherJudgmentDate: '2022-02-02',
  relatedCitation: null,
  relationDate: '2022-02-02',
  sourceReference: 'حكم الطعن 9',
  notes: 'نقض كلي',
  ...overrides,
});

const WARNING = {
  judgmentId: 'j1',
  finality: 'FINAL' as const,
  status: 'ACTIVE' as const,
  relationCount: 1,
  relationTypes: ['OVERTURNED' as const],
  warnings: ['LATER_RELATION_EXISTS'],
  legalValidityDecided: false,
  note: 'n',
};

describe('judgment detail', () => {
  it('shows the record, the principle apart from the judgment text, the warning and the visible linked cases', async () => {
    script(() =>
      jsonResponse(
        judgment({
          principles: [principle({ reviewStatus: 'REVIEWED' })],
          relations: { outgoing: [relation()], incoming: [relation({ id: 'r2', direction: 'INCOMING', otherJudgmentId: 'j3', otherSubject: 'حكم سابق' })] },
          validity: WARNING,
          linkedCases: [{ linkId: 'l1', caseId: 'c1', referenceNo: 'LD-2026-00001', title: 'قضية اختبار', reason: 'نفس المسألة' }],
        }),
      ),
    );
    renderLibrary('/library/j1');

    expect(await screen.findByRole('heading', { level: 1, name: 'تعويض عن قرار إداري معيب' })).toBeInTheDocument();
    expect(screen.getByText('حكمت المحكمة بالتعويض')).toBeInTheDocument();
    expect(screen.getByText('مجموعة الأحكام')).toBeInTheDocument();
    expect(screen.getByText('Administrative liability')).toBeInTheDocument();
    expect(screen.getByText('تعويض, قرار إداري')).toBeInTheDocument();
    // Principle apart, with its review state and the reminder that it does not replace the judgment.
    expect(screen.getByText('الإنهاء بغير سبب مشروع يوجب التعويض')).toBeInTheDocument();
    expect(screen.getByText('Reviewed')).toBeInTheDocument();
    expect(screen.getByText(/does not replace the text of the judgment/)).toBeInTheDocument();
    // Standing: a warning and the relation with its source, never a verdict.
    expect(screen.getAllByText('A later event is recorded for this judgment: check the sources below.').length).toBeGreaterThan(0);
    expect(screen.getByText(/does not decide whether the judgment is still good law/)).toBeInTheDocument();
    expect(screen.getAllByText('Overturned').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Source: حكم الطعن 9').length).toBe(2);
    expect(screen.getByText('This judgment is the other side of')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /LD-2026-00001 - قضية اختبار/ })).toHaveAttribute('href', '/cases/c1?lang=en');
  });

  it('reads the missing, forbidden and failed answers', async () => {
    script(() => problem(404));
    const missing = renderLibrary('/library/j1');
    expect(await screen.findByRole('heading', { name: /not found/i })).toBeInTheDocument();
    missing.unmount();
    script(() => problem(403));
    const denied = renderLibrary('/library/j1');
    expect(await screen.findByRole('heading', { name: /access/i })).toBeInTheDocument();
    denied.unmount();
    script(() => problem(500));
    renderLibrary('/library/j1');
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded');
  });

  it('shows what is not specified, hides the parties of a restricted judgment and offers readers no change', async () => {
    script(() =>
      jsonResponse(
        judgment({
          courtLevel: null,
          chamber: null,
          caseType: null,
          direction: null,
          partiesDescription: null,
          partiesMasked: true,
          confidentiality: 'RESTRICTED',
          keywords: [],
          classifications: [],
          copyReference: null,
          summary: null,
          operativeText: null,
          reasons: null,
          legalTexts: null,
          requestsAddressed: null,
          requestResults: null,
          judgmentDate: null,
          caseNumber: null,
          caseYear: null,
          finality: 'NOT_SPECIFIED',
        }),
      ),
    );
    renderLibrary('/library/j1', user(['LIBRARY_VIEW']));
    expect(await screen.findByText('The parties are hidden because the judgment is restricted.')).toBeInTheDocument();
    expect(screen.getAllByText('Not specified').length).toBeGreaterThan(3);
    expect(screen.queryByRole('link', { name: 'Edit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Withdraw from use' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add principle' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Record event' })).not.toBeInTheDocument();
    expect(screen.getByText('No principle extracted yet.')).toBeInTheDocument();
    expect(screen.getByText('No later event recorded.')).toBeInTheDocument();
    expect(screen.getByText('No case you may see uses this judgment.')).toBeInTheDocument();
  });

  it('withdraws with a reason, and reinstates a withdrawn judgment', async () => {
    let withdrawn = false;
    const { calls } = script((call) => {
      if (call.method === 'POST' && call.path.endsWith('/withdraw')) {
        withdrawn = true;
        return jsonResponse(judgment({ status: 'WITHDRAWN' }));
      }
      if (call.method === 'POST' && call.path.endsWith('/reinstate')) {
        withdrawn = false;
        return jsonResponse(judgment());
      }
      return jsonResponse(
        withdrawn
          ? judgment({ status: 'WITHDRAWN', withdrawnReason: 'أُدخل بالخطأ', validity: { ...WARNING, relationCount: 0, warnings: ['WITHDRAWN'], relationTypes: [] } })
          : judgment(),
      );
    });
    const typing = userEvent.setup();
    renderLibrary('/library/j1');
    await typing.click(await screen.findByRole('button', { name: 'Withdraw from use' }));
    await typing.click(screen.getByRole('button', { name: 'Withdraw' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    await typing.type(screen.getByLabelText('Reason for withdrawing'), 'أُدخل بالخطأ');
    await typing.click(screen.getByRole('button', { name: 'Withdraw' }));
    expect(await screen.findByText('This judgment is withdrawn: أُدخل بالخطأ')).toBeInTheDocument();
    expect(calls.find((c) => c.path.endsWith('/withdraw'))?.body).toEqual({ reason: 'أُدخل بالخطأ', version: 3 });
    await typing.click(screen.getByRole('button', { name: 'Reinstate' }));
    await waitFor(() => {
      expect(screen.queryByText(/This judgment is withdrawn/)).not.toBeInTheDocument();
    });
  });

  it('tells a conflict on withdrawal from another failure', async () => {
    script((call) => (call.method === 'POST' ? problem(409) : jsonResponse(judgment())));
    const typing = userEvent.setup();
    renderLibrary('/library/j1');
    await typing.click(await screen.findByRole('button', { name: 'Withdraw from use' }));
    await typing.type(screen.getByLabelText('Reason for withdrawing'), 'x');
    await typing.click(screen.getByRole('button', { name: 'Withdraw' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone changed this record meanwhile');
  });

  it('refuses to reinstate on a conflict and on a plain failure', async () => {
    let status = 409;
    script((call) => (call.method === 'POST' ? problem(status) : jsonResponse(judgment({ status: 'WITHDRAWN', withdrawnReason: 'r' }))));
    const typing = userEvent.setup();
    renderLibrary('/library/j1');
    await typing.click(await screen.findByRole('button', { name: 'Reinstate' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone changed this record meanwhile');
    status = 500;
    await typing.click(screen.getByRole('button', { name: 'Reinstate' }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('could not be saved');
    });
  });

  it('adds, edits and reviews principles; editing warns that review starts again', async () => {
    let principles: Principle[] = [];
    const { calls } = script((call) => {
      if (call.method === 'POST' && call.path === '/api/v1/library/judgments/j1/principles') {
        principles = [principle()];
        return jsonResponse(principle(), 201);
      }
      if (call.method === 'PUT') {
        principles = [principle({ principleText: 'معدل', version: 1 })];
        return jsonResponse(principles[0]);
      }
      if (call.method === 'POST' && call.path.endsWith('/review')) {
        principles = [principle({ reviewStatus: 'REVIEWED', version: 2 })];
        return jsonResponse(principles[0]);
      }
      return jsonResponse(judgment({ principles }));
    });
    const typing = userEvent.setup();
    renderLibrary('/library/j1');
    await typing.click(await screen.findByRole('button', { name: 'Add principle' }));
    await typing.click(screen.getByRole('button', { name: 'Save principle' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Write the principle.');
    await typing.type(screen.getByLabelText('Principle'), 'الإنهاء بغير سبب');
    await typing.type(screen.getByLabelText('Legal topic'), 'الفصل');
    await typing.click(screen.getByRole('button', { name: 'Save principle' }));
    expect(await screen.findByText('Principle saved.')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'POST' && c.path.endsWith('/principles'))?.body).toMatchObject({
      principleText: 'الإنهاء بغير سبب',
      legalTopic: 'الفصل',
      legalBasis: null,
    });

    await typing.click(await screen.findByRole('button', { name: 'Edit' }));
    expect(screen.getByText(/returns the principle to draft/)).toBeInTheDocument();
    await typing.clear(screen.getByLabelText('Principle'));
    await typing.type(screen.getByLabelText('Principle'), 'معدل');
    await typing.click(screen.getByRole('button', { name: 'Save principle' }));
    expect(await screen.findByText('معدل')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'PUT')?.body).toMatchObject({ principleText: 'معدل', version: 0 });

    await typing.click(screen.getByRole('button', { name: 'Mark as reviewed' }));
    expect(await screen.findByText('Reviewed')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Mark as reviewed' })).not.toBeInTheDocument();
  });

  it('cancels a principle form and explains a conflict and a failed review', async () => {
    script((call) => {
      if (call.method === 'PUT') return problem(409);
      if (call.method === 'POST') return problem(500);
      return jsonResponse(judgment({ principles: [principle({ legalTopic: null, legalBasis: null, factsApplied: null, scopeNote: null, memberNotes: null })] }));
    });
    const typing = userEvent.setup();
    renderLibrary('/library/j1');
    await typing.click(await screen.findByRole('button', { name: 'Add principle' }));
    await typing.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.getByRole('button', { name: 'Add principle' })).toBeInTheDocument();
    await typing.click(screen.getByRole('button', { name: 'Edit' }));
    await typing.click(screen.getByRole('button', { name: 'Save principle' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Someone changed this record meanwhile');
    await typing.click(screen.getByRole('button', { name: 'Cancel' }));
    await typing.click(screen.getByRole('button', { name: 'Mark as reviewed' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be saved');
  });

  it('records a relation with its source and removes one', async () => {
    let relations: Relation[] = [relation({ id: 'r9', otherJudgmentId: null, otherSubject: null, relatedCitation: 'الطعن 77' })];
    const { calls } = script((call) => {
      if (call.path === '/api/v1/library/judgments' && call.method === 'GET') {
        return jsonResponse(page([listItem({ id: 'j1' }), listItem({ id: 'j2', subject: 'حكم لاحق' })]));
      }
      if (call.method === 'POST' && call.path.endsWith('/relations')) {
        relations = [...relations, relation({ id: 'r10' })];
        return jsonResponse({}, 201);
      }
      if (call.method === 'DELETE') {
        relations = [];
        return new Response(null, { status: 204 });
      }
      return jsonResponse(judgment({ relations: { outgoing: relations, incoming: [] }, validity: WARNING }));
    });
    const typing = userEvent.setup();
    renderLibrary('/library/j1');
    expect(await screen.findByText('الطعن 77')).toBeInTheDocument();
    await typing.click(screen.getByRole('button', { name: 'Record event' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Give the source');
    await typing.selectOptions(screen.getByLabelText('Event'), 'LATER');
    await typing.click(screen.getByLabelText('Judgment in the library (optional)'));
    await typing.selectOptions(screen.getByLabelText('Judgment in the library (optional)'), 'j2');
    await typing.type(screen.getByLabelText('Date of the event'), '2022-02-02');
    await typing.type(screen.getByLabelText('Source of this information'), 'حكم الطعن 9');
    await typing.type(screen.getByLabelText('Notes'), 'ملاحظة');
    await typing.click(screen.getByRole('button', { name: 'Record event' }));
    expect(await screen.findByText('Event recorded.')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'POST' && c.path.endsWith('/relations'))?.body).toEqual({
      relationType: 'LATER',
      relatedJudgmentId: 'j2',
      relatedCitation: null,
      relationDate: '2022-02-02',
      sourceReference: 'حكم الطعن 9',
      notes: 'ملاحظة',
    });
    const first = screen.getAllByRole('button', { name: 'Remove' })[0];
    expect(first).toBeDefined();
    if (first) await typing.click(first);
    expect(await screen.findByText('Event removed.')).toBeInTheDocument();
  });

  it('records a relation by citation and explains failures', async () => {
    let fail = true;
    const { calls } = script((call) => {
      if (call.path === '/api/v1/library/judgments' && call.method === 'GET') return problem(500);
      if (call.method === 'POST') return fail ? problem(500) : jsonResponse({}, 201);
      if (call.method === 'DELETE') return problem(500);
      return jsonResponse(judgment({ relations: { outgoing: [relation()], incoming: [] }, validity: WARNING }));
    });
    const typing = userEvent.setup();
    renderLibrary('/library/j1');
    await typing.click(await screen.findByLabelText('Judgment in the library (optional)'));
    await typing.type(screen.getByLabelText('Decision outside the library (citation)'), 'الطعن 5');
    await typing.type(screen.getByLabelText('Source of this information'), 'مصدر');
    await typing.click(screen.getByRole('button', { name: 'Record event' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be saved');
    fail = false;
    await typing.click(screen.getByRole('button', { name: 'Record event' }));
    expect(await screen.findByText('Event recorded.')).toBeInTheDocument();
    expect(calls.filter((c) => c.method === 'POST').at(-1)?.body).toMatchObject({ relatedCitation: 'الطعن 5', relatedJudgmentId: null, relationDate: null, notes: null });
    await typing.click(screen.getByRole('button', { name: 'Remove' }));
    await waitFor(() => {
      expect(within(screen.getByRole('alert')).getByText(/could not be saved/)).toBeInTheDocument();
    });
  });

  it('keeps the classification label language of the page', async () => {
    script(() => jsonResponse(judgment({ classifications: [classification({ labelEn: null })] })));
    renderLibrary('/library/j1?lang=en');
    expect(await screen.findByText('المسؤولية الإدارية')).toBeInTheDocument();
  });
});
