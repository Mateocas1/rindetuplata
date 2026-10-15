/**
 * Class strings shared by the account islands (T7d, T13d).
 *
 * Thin aliases over the T13a recipes in `styles/recipes.css` (card, field,
 * buttons) plus the seven Cobalto Cristal text roles, so every island shares one
 * shape and `tokens.css` stays the single source of color. A field label is the
 * mono caps `type-label`; help and error lines are `type-body` (muted or loss)
 * and stay tied to their control with `aria-describedby`. The `field` recipe is
 * 16px on mobile so iOS Safari never zooms.
 */
export const inputClass = 'field max-w-64 tabular-nums';

/** Free text: no `tabular-nums`, and no narrow measure. */
export const textareaClass = 'field';

/** Control labels: mono caps, never a second size. */
export const labelClass = 'type-label';

export const fieldGroupClass = 'flex flex-col gap-1';

export const buttonPrimaryClass = 'btn-primary';

export const buttonSecondaryClass = 'btn-secondary';

export const cardClass = 'card p-block sm:p-6';

export const errorTextClass = 'type-body text-loss';

export const noticeClass = 'flex flex-col gap-1 rounded-panel border px-4 py-3';

/**
 * A quiet inline action (the picker's "Quitar"): the direction keeps cobalt for
 * the one primary action of a view, so it is ink and underlined, never `link`.
 */
export const inlineActionClass =
  'type-body underline decoration-from-font underline-offset-2 hover:decoration-transparent';
