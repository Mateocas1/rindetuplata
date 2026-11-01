/**
 * Rendered-markup rules for the account views (T12c).
 *
 * The account islands are client-only, so a Node render is the cheapest way to
 * assert on their markup: `renderToStaticMarkup` runs no effect, which is
 * exactly the states under test (a notice, a resting list, a form).
 *
 * Two rules live here, both stated by the T12c design notes:
 *
 * 1. A notice never carries its meaning in colour alone: every status surface
 *    renders an `aria-hidden` icon beside a visible title, and no panel may
 *    hand-roll a tone surface of its own (WCAG 1.4.1).
 * 2. A view has one primary action: at most one `btn-primary` in the rendered
 *    markup, and the destructive confirmation confirms through the loss role
 *    while the create form keeps the alerts view's only primary.
 * 3. The direction (T13d) replaced the T12 text steps with the seven `type-*`
 *    roles (the retired steps were deleted in T13e), and a field label wears the
 *    mono caps `type-label` role; no account surface may size text by hand.
 */
import { readFileSync, readdirSync } from 'node:fs';
import { basename, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createElement as h } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import type { AlertRule, WalletRate } from '../../lib/account';
import type { AlertFormState } from '../../lib/accountForm';
import AlertCreateForm from './AlertCreateForm';
import AlertRuleList from './AlertRuleList';
import LoginPanel from './LoginPanel';
import ProblemNotice from './ProblemNotice';
import StatusNotice from './StatusNotice';
import WalletRateEditor from './WalletRateEditor';
import WalletAuditTable from './WalletAuditTable';
import WalletTierTable from './WalletTierTable';
import { buttonPrimaryClass, buttonSecondaryClass } from './ui';

const accountDir = fileURLToPath(new URL('.', import.meta.url));
const webRoot = resolve(accountDir, '../../..');

/** The three account pages, whose own markup is part of the same migration. */
const accountPages = ['src/pages/ingresar.astro', 'src/pages/alertas.astro', 'src/pages/admin/tasas.astro'];

/** Every class token of every `class` attribute in a rendered tree. */
function classTokens(html: string): string[] {
  return [...html.matchAll(/class="([^"]*)"/g)]
    .flatMap((match) => match[1].split(/\s+/))
    .filter((token) => token !== '');
}

function countClass(html: string, token: string): number {
  return classTokens(html).filter((current) => current === token).length;
}

/** Decorative icons: the non-colour cue of a notice. */
function countIcons(html: string): number {
  return [...html.matchAll(/<svg[^>]*aria-hidden="true"/g)].length;
}

function accountSources(): string[] {
  return readdirSync(accountDir)
    .filter((name) => (name.endsWith('.tsx') || name.endsWith('.ts')) && !name.endsWith('.test.ts'))
    .map((name) => join(accountDir, name));
}

/** The account sources plus the pages that host their islands. */
function accountFiles(): string[] {
  return [...accountSources(), ...accountPages.map((page) => join(webRoot, page))];
}

/** The failure notice of a request that never got an answer. */
const PROBLEM = { status: 500, message: 'boom' };

const ALERT: AlertRule = {
  id: 7,
  type: 'THRESHOLD',
  kind: 'WALLET',
  instrumentId: null,
  thresholdPercent: 25,
  active: true,
  createdAt: '2026-10-20T09:00:00Z',
};

const RATE: WalletRate = {
  walletId: 'mercado-pago',
  name: 'Mercado Pago',
  tiers: [{ floor: null, ceiling: null, tnaPercent: 25 }],
  conditions: [],
  note: null,
  verifiedAt: '2026-10-20',
  daysSinceVerified: 1,
  updatedAt: '2026-10-20T12:00:00Z',
  updatedBy: 'operador@rindetuplata.com',
};

const noop = (): void => undefined;

const EMPTY_FORM: AlertFormState = { type: 'THRESHOLD', kind: '', instrumentId: '', thresholdPercent: '' };

function createForm(props: Partial<Parameters<typeof AlertCreateForm>[0]> = {}): string {
  return renderToStaticMarkup(
    h(AlertCreateForm, {
      form: EMPTY_FORM,
      errors: {},
      instruments: [],
      optionsFailed: false,
      creating: false,
      onChange: noop,
      onSubmit: noop,
      ...props,
    }),
  );
}

function alertsList(props: Partial<Parameters<typeof AlertRuleList>[0]> = {}): string {
  return renderToStaticMarkup(
    h(AlertRuleList, {
      rules: [ALERT],
      instruments: [],
      confirmingId: null,
      deletingId: null,
      onRequestDelete: noop,
      onCancelDelete: noop,
      onConfirmDelete: noop,
      ...props,
    }),
  );
}

describe('a notice never carries its meaning in colour alone', () => {
  it('renders the failure notice with an icon beside its title', () => {
    const html = renderToStaticMarkup(h(ProblemNotice, { problem: PROBLEM }));

    expect(countIcons(html), 'the notice needs an icon, not only a red fill').toBe(1);
    expect(html).toContain('No pudimos completar la operación');
    expect(countClass(html, 'bg-loss-surface')).toBe(1);
  });

  it('offers the retry as a peer action, never as a second primary', () => {
    const html = renderToStaticMarkup(h(ProblemNotice, { problem: PROBLEM, onRetry: noop }));

    expect(html).toContain('Reintentar');
    expect(countClass(html, buttonPrimaryClass)).toBe(0);
    expect(countClass(html, buttonSecondaryClass)).toBe(1);
  });

  it('renders a result notice with an icon beside its title', () => {
    const html = renderToStaticMarkup(
      h(StatusNotice, { role: 'status', tone: 'gain', title: 'Alerta creada' }, 'lista'),
    );

    expect(countIcons(html)).toBe(1);
    expect(html).toContain('Alerta creada');
    expect(countClass(html, 'bg-gain-surface')).toBe(1);
  });

  it('keeps every tone surface inside the one shared notice component', () => {
    const offenders = accountSources()
      .filter((file) => basename(file) !== 'StatusNotice.tsx')
      .filter((file) =>
        /bg-(loss|gain|warning)-surface|border-(loss|gain|warning)\b|bg-accent-solid/.test(
          readFileSync(file, 'utf8'),
        ),
      )
      .map((file) => basename(file));

    expect(offenders, 'a hand-rolled notice would drift from the shared one').toEqual([]);
  });
});

describe('a view has one primary action', () => {
  it('renders exactly one primary button on the login panel', () => {
    expect(countClass(renderToStaticMarkup(h(LoginPanel, {})), buttonPrimaryClass)).toBe(1);
  });

  it('renders exactly one primary button in the wallet rate editor', () => {
    const html = renderToStaticMarkup(
      h(WalletRateEditor, {
        rate: RATE,
        onSaved: noop,
        onCancel: noop,
        onProblem: noop,
      }),
    );

    expect(countClass(html, buttonPrimaryClass)).toBe(1);
    expect(countClass(html, buttonSecondaryClass)).toBeGreaterThanOrEqual(1);
  });

  it('keeps the alerts view down to the create form primary', () => {
    const form = renderToStaticMarkup(
      h(AlertCreateForm, {
        form: EMPTY_FORM,
        errors: {},
        instruments: [],
        optionsFailed: false,
        creating: false,
        onChange: noop,
        onSubmit: noop,
      }),
    );

    expect(countClass(form, buttonPrimaryClass), 'the create form owns the primary').toBe(1);
    expect(countClass(alertsList(), buttonPrimaryClass), 'the resting list adds none').toBe(0);
    expect(
      countClass(alertsList({ confirmingId: ALERT.id }), buttonPrimaryClass),
      'the confirmation is loss-toned, not a second primary',
    ).toBe(0);
  });

  it('confirms a deletion with the loss role and a secondary cancel', () => {
    const html = alertsList({ confirmingId: ALERT.id });

    expect(html).toContain('Sí, eliminar la alerta');
    expect(html).toContain('Cancelar');
    // The loss fill on on-accent text lives in the btn-danger recipe (motion.test.ts pins its values).
    expect(countClass(html, 'btn-danger')).toBe(1);
    expect(countClass(html, buttonSecondaryClass)).toBe(1);
  });

  it('labels the row it deletes and keeps its threshold readable', () => {
    const html = alertsList();

    expect(html).toContain('Eliminar la alerta de Billetera sobre Cualquier opción');
    expect(html).toContain('25,00%');
    // The threshold is the row's protagonist, in the mono tabular data role.
    expect(countClass(html, 'type-data')).toBeGreaterThanOrEqual(1);
  });
});

describe('the wallet audit table', () => {
  it('heads with mono caps labels and reads in mono tabular cells', () => {
    const html = renderToStaticMarkup(
      h(WalletAuditTable, {
        entries: [
          {
            id: 1,
            walletId: 'naranja-x',
            action: 'CONFIRMED',
            payload: { name: 'Naranja X' },
            actor: 'operador@rindetuplata.com',
            at: '2026-10-20T12:00:00Z',
          },
        ],
      }),
    );

    expect(html).toContain('role="region"');
    expect(html).toContain('tabindex="0"');
    expect(countClass(html, 'type-label'), 'the four column heads and the detail summary').toBe(5);
    expect(countClass(html, 'type-data'), 'actor, date and the stored document').toBeGreaterThanOrEqual(3);
  });
});

describe('the wallet tier table', () => {
  it('scrolls inside its own labelled region instead of spilling out of the card', () => {
    const html = renderToStaticMarkup(
      h(WalletTierTable, { label: 'Franjas de tasa', tiers: RATE.tiers }),
    );

    expect(html).toContain('role="region"');
    expect(html).toContain('tabindex="0"');
    expect(html).toContain('aria-label="Franjas de tasa"');
    expect(countClass(html, 'type-label'), 'mono caps heads like the ranking').toBe(3);
    expect(countClass(html, 'type-data'), 'mono tabular cells like the ranking').toBeGreaterThanOrEqual(3);
  });
});

describe('the alert types (T6c, L32 a)', () => {
  it('opens on the threshold rule with the fields it always had', () => {
    const html = createForm();

    expect(html).toContain('>Tipo de alerta</label>');
    expect(html).toContain('>Tipo de opción</label>');
    expect(html).toContain('>Avisame si supera esta tasa</label>');
    expect(html).toContain('<option value="THRESHOLD" selected="">Superó una tasa</option>');
    for (const label of ['Superó una tasa', 'Cambió la mejor opción', 'Bajó una tasa', 'Resumen semanal']) {
      expect(html, `the selector offers ${label}`).toContain(`>${label}</option>`);
    }
  });

  it('asks only for a kind when the best option changes or for the weekly digest', () => {
    for (const type of ['BEST_CHANGED', 'WEEKLY_DIGEST'] as const) {
      const html = createForm({ form: { ...EMPTY_FORM, type } });

      expect(html, type).toContain('>Tipo de opción</label>');
      expect(html, type).not.toContain('Avisame si supera esta tasa');
      expect(html, type).not.toContain('Buscar por nombre, banco o fondo');
      expect(countClass(html, buttonPrimaryClass), type).toBe(1);
    }
  });

  it('asks for the bank or wallet to follow, and no kind or threshold, when a rate drops', () => {
    const html = createForm({ form: { ...EMPTY_FORM, type: 'RATE_DROP' } });

    expect(html).toContain('Banco o billetera a seguir');
    expect(html).toContain('Buscar por nombre, banco o fondo');
    expect(html).not.toContain('>Tipo de opción</label>');
    expect(html).not.toContain('Avisame si supera esta tasa');
  });

  it('shows the error of a missing bank or wallet beside its field', () => {
    const html = createForm({
      form: { ...EMPTY_FORM, type: 'RATE_DROP' },
      errors: { instrumentId: 'Elegí el banco o la billetera que querés seguir.' },
    });

    expect(html).toContain('Elegí el banco o la billetera que querés seguir.');
  });

  it('lists a rule of each type under its own name and a delete button that says which', () => {
    const rule = (fields: Partial<AlertRule>): AlertRule => ({ ...ALERT, ...fields });

    const best = alertsList({
      rules: [rule({ id: 8, type: 'BEST_CHANGED', kind: null, thresholdPercent: null })],
    });
    expect(best).toContain('Cambió la mejor opción');
    expect(best).toContain('Si cambia la mejor');
    expect(best).toContain('Eliminar la alerta de Cambió la mejor opción sobre Cualquier tipo');
    expect(best).not.toContain('NaN');

    const digest = alertsList({
      rules: [rule({ id: 9, type: 'WEEKLY_DIGEST', kind: 'FUND', thresholdPercent: null })],
    });
    expect(digest).toContain('Resumen semanal');
    expect(digest).toContain('Cada semana');
    expect(digest).toContain('Eliminar la alerta de Resumen semanal sobre Fondo');

    const drop = alertsList({
      rules: [rule({ id: 10, type: 'RATE_DROP', kind: 'WALLET', instrumentId: 44, thresholdPercent: null })],
    });
    expect(drop).toContain('Bajó una tasa');
    expect(drop).toContain('Si baja su tasa');
    expect(drop).toContain('Eliminar la alerta de Bajó una tasa sobre La opción #44');
  });

  it('lists a threshold rule exactly as before', () => {
    const html = alertsList();

    expect(html).toContain('Avisa si la tasa supera');
    expect(html).toContain('25,00%');
    expect(html).toContain('Eliminar la alerta de Billetera sobre Cualquier opción');
  });
});

describe('account text stays on the seven type roles', () => {
  it('keeps no T12 text step in the account sources or pages', () => {
    const legacy = /\btext-(caption|body-sm|h1|h2|h3)\b/;
    const offenders = accountFiles()
      .filter((file) => legacy.test(readFileSync(file, 'utf8')))
      .map((file) => relative(webRoot, file));

    expect(offenders, 'a T12 text step survived the T13d migration').toEqual([]);
  });

  it('renders a field label with the mono caps label role', () => {
    const html = renderToStaticMarkup(
      h(AlertCreateForm, {
        form: EMPTY_FORM,
        errors: {},
        instruments: [],
        optionsFailed: false,
        creating: false,
        onChange: noop,
        onSubmit: noop,
      }),
    );

    expect(html).toContain('class="type-label">Tipo de opción</label>');
    expect(html).toContain('class="type-label">Avisame si supera esta tasa</label>');
  });
});
