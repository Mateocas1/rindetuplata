/**
 * Class strings shared by the account islands (T7d).
 *
 * Thin aliases over the T12a recipes in `styles/recipes.css` (card, field,
 * buttons), so every island shares one shape and `tokens.css` stays the single
 * source of color. The `field` recipe is 16px on mobile so iOS Safari never zooms.
 */
export const inputClass = 'field max-w-64 tabular-nums';

/** Free text: no `tabular-nums`, and no narrow measure. */
export const textareaClass = 'field';

export const labelClass = 'text-body-sm font-semibold';

export const fieldGroupClass = 'flex flex-col gap-1';

export const buttonPrimaryClass = 'btn-primary';

export const buttonSecondaryClass = 'btn-secondary';

export const cardClass = 'card p-block sm:p-6';

export const errorTextClass = 'text-body-sm text-loss';

export const noticeClass = 'flex flex-col gap-1 rounded-lg border px-4 py-3';
