import { expect, test, type APIRequestContext } from '@playwright/test';

/**
 * End-to-end smoke over the local Compose stack (T7e).
 *
 * It closes the gap T7d left open: nothing executed the account islands against a real API. This spec
 * drives the product the way a reader does and always through Caddy (one origin): it asks for a magic
 * link on `/ingresar`, takes the link from Mailpit's HTTP API — the only delivery channel of the MVP
 * (S13) —, logs in, creates and deletes an alert, logs out, and checks that `/alertas` without a
 * session sends the reader back to `/ingresar`.
 *
 * `deploy/e2e.sh` builds the API image, brings the stack up and builds the site against it, so this
 * spec can assume a healthy stack with an empty database: the ranking then shows its empty state and
 * the site still builds and serves.
 */

const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:4321';
const MAILPIT_URL = (process.env.MAILPIT_URL ?? 'http://localhost:8025').replace(/\/+$/, '');
const EMAIL = 'e2e@example.com';
const THRESHOLD_PERCENT = '25';
const MAGIC_LINK_TIMEOUT_MS = 20_000;

interface MailpitAddress {
  Address: string;
}

interface MailpitSummary {
  ID: string;
  To?: MailpitAddress[];
}

interface MailpitMessage {
  ID: string;
  Subject?: string;
  Text?: string;
  HTML?: string;
}

/** The messages Mailpit already holds: a stack that was not recreated keeps the mails of earlier runs. */
async function messageIds(request: APIRequestContext): Promise<Set<string>> {
  const list = await request.get(`${MAILPIT_URL}/api/v1/messages`, { params: { limit: '50' } });
  expect(list.status(), 'Mailpit must answer its message list').toBe(200);
  const body = (await list.json()) as { messages?: MailpitSummary[] };
  return new Set((body.messages ?? []).map((message) => message.ID));
}

async function messageFor(
  request: APIRequestContext,
  seen: Set<string>,
): Promise<MailpitSummary | undefined> {
  const list = await request.get(`${MAILPIT_URL}/api/v1/messages`, { params: { limit: '50' } });
  expect(list.status(), 'Mailpit must answer its message list').toBe(200);
  const body = (await list.json()) as { messages?: MailpitSummary[] };
  return body.messages?.find(
    (message) => !seen.has(message.ID) && message.To?.some((to) => to.Address === EMAIL),
  );
}

/**
 * The magic link the API just mailed, read from Mailpit. The mail arrives asynchronously, and only a
 * message that was not there before this request counts: on a reused stack an earlier run's link is
 * still stored, and replaying it (already consumed) would fail the login for the wrong reason.
 */
async function waitForMagicLink(request: APIRequestContext, seen: Set<string>): Promise<string> {
  const deadline = Date.now() + MAGIC_LINK_TIMEOUT_MS;
  for (;;) {
    const summary = await messageFor(request, seen);
    if (summary !== undefined) {
      const detail = await request.get(`${MAILPIT_URL}/api/v1/message/${summary.ID}`);
      expect(detail.status(), 'Mailpit must answer the message body').toBe(200);
      const message = (await detail.json()) as MailpitMessage;
      const body = `${message.Text ?? ''}\n${message.HTML ?? ''}`;
      const link = body.match(/https?:\/\/[^\s"'<>]+\/ingresar\?token=[A-Za-z0-9_-]+/)?.[0];
      if (link !== undefined) return link;
    }
    if (Date.now() > deadline) {
      throw new Error(`no magic link for ${EMAIL} in Mailpit after ${MAGIC_LINK_TIMEOUT_MS} ms`);
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
}

test('the site and the API answer through Caddy', async ({ request }) => {
  const home = await request.get('/');
  expect(home.status(), 'the prerendered home page').toBe(200);
  expect(await home.text()).toContain('¿Dónde rinde tu plata hoy?');

  const category = await request.get('/billeteras/');
  expect(category.status(), 'a category landing page').toBe(200);

  const csrf = await request.get('/api/v1/auth/csrf');
  expect(csrf.status(), 'the CSRF endpoint, proxied to the API').toBe(200);
  const token = (await csrf.json()) as { headerName?: string; token?: string };
  expect(token.headerName).toBe('X-XSRF-TOKEN');
  expect(token.token ?? '').not.toBe('');
});

test('a magic link logs a reader in, and their alerts work', async ({ page, request }) => {
  await page.goto('/ingresar');
  // Read before asking, so the link below is this request's and not a stored one.
  const storedMessages = await messageIds(request);
  await page.getByLabel('Tu email').fill(EMAIL);
  await page.getByRole('button', { name: 'Mandarme el link para entrar' }).click();
  await expect(page.getByText('Revisá tu email')).toBeVisible();

  const link = await waitForMagicLink(request, storedMessages);
  expect(
    new URL(link).origin,
    'the magic link must point at the site, never at the API (RINDETUPLATA_AUTH_MAGICLINKURL)',
  ).toBe(new URL(BASE_URL).origin);

  await page.goto(link);
  await expect(page).toHaveURL(/\/alertas\/?$/);

  // A manual run reuses the stack that is already up, so the account may still hold rules from an
  // earlier run. Clear them first: the assertions below are about this run's alert, and the empty
  // state has to be reachable whatever the database held. (`deploy/e2e.sh` starts from a fresh one.)
  const alerts = page.getByRole('region', { name: 'Tus alertas' });
  const deleteButtons = alerts.getByRole('button', { name: /^Eliminar la alerta de/ });
  for (let pending = await deleteButtons.count(); pending > 0; pending = await deleteButtons.count()) {
    await deleteButtons.first().click();
    await page.getByRole('button', { name: 'Sí, eliminar la alerta' }).click();
    await expect(deleteButtons).toHaveCount(pending - 1);
  }
  await expect(page.getByText('Todavía no tenés alertas')).toBeVisible();

  await page.getByLabel('Tipo de opción').selectOption('WALLET');
  await page.getByLabel(/Avisame si supera esta tasa/).fill(THRESHOLD_PERCENT);
  await page.getByRole('button', { name: 'Crear alerta' }).click();

  // Scoped to the alerts region: the header navigation is a list too (Billeteras, Fondos, ...).
  const created = alerts.getByRole('listitem').filter({ hasText: 'Billetera' });
  await expect(created, 'the new alert is listed').toBeVisible();
  await expect(created).toContainText('25,00%');

  await page.getByRole('button', { name: /^Eliminar la alerta de/ }).click();
  await page.getByRole('button', { name: 'Sí, eliminar la alerta' }).click();
  await expect(page.getByText('Todavía no tenés alertas')).toBeVisible();

  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page).toHaveURL(/\/ingresar\/?$/);

  await page.goto('/alertas');
  await expect(page, 'no session, no alerts page').toHaveURL(/\/ingresar\/?$/);
});
