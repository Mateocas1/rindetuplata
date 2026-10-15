/**
 * Class strings shared by the account islands (T7d).
 *
 * Only token-backed utilities, so `tokens.css` stays the single source of
 * colour and T12 can reskin the whole product by repointing the roles. Inputs
 * are 16px on mobile (`text-base sm:text-body-sm`) so iOS Safari never zooms.
 */
export const inputClass =
  'w-full max-w-64 rounded-md border border-border-strong bg-surface px-3 py-2 text-base tabular-nums sm:text-body-sm';

/** Free text: no `tabular-nums`, and no narrow measure. */
export const textareaClass =
  'w-full rounded-md border border-border-strong bg-surface px-3 py-2 text-base sm:text-body-sm';

export const labelClass = 'text-body-sm font-semibold';

export const fieldGroupClass = 'flex flex-col gap-1';

export const buttonPrimaryClass =
  'rounded-md bg-accent-solid px-4 py-2 text-body-sm font-semibold text-on-accent hover:bg-accent disabled:cursor-not-allowed disabled:opacity-60';

export const buttonSecondaryClass =
  'rounded-md border border-border-strong bg-surface-raised px-3 py-2 text-body-sm font-semibold text-text hover:bg-subtle-surface';

export const cardClass = 'rounded-lg border border-border bg-surface-raised p-block sm:p-6';

export const errorTextClass = 'text-body-sm text-loss';

export const noticeClass = 'flex flex-col gap-1 rounded-lg border px-4 py-3';
