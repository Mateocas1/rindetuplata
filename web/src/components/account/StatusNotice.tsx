/**
 * The one status surface of the account panels (T12c).
 *
 * A failure, a result and a warning wear the same shape: an `aria-hidden` icon
 * and a visible title over a tinted surface, so what happened never rides on
 * colour alone (WCAG 1.4.1). The icon is decorative because the title already
 * names the outcome; its stroke follows the title's weight.
 *
 * `role` is the announcement, not the tint: `alert` is an urgent failure with no
 * field to attach the message to, `status` is a result the reader can take in
 * whenever they get to it.
 */
import type { ReactNode } from 'react';

export type NoticeTone = 'loss' | 'gain' | 'warning';

export interface StatusNoticeProps {
  role: 'alert' | 'status';
  tone: NoticeTone;
  /** The outcome, in the reader's words: "Sin permiso", "Alerta creada". */
  title: string;
  children?: ReactNode;
}

/** The tint of each tone; the border keeps the surface readable in forced colors. */
const SURFACE: Record<NoticeTone, string> = {
  loss: 'border-loss bg-loss-surface text-on-loss',
  gain: 'border-gain bg-gain-surface text-on-gain',
  warning: 'border-warning bg-warning-surface text-on-warning',
};

/** 20px box, `currentColor` stroke, 1.75 stroke weight to match a semibold title. */
const ICON: Record<NoticeTone, ReactNode> = {
  loss: (
    <>
      <circle cx="10" cy="10" r="7.6" />
      <path d="M10 5.75v5M10 14.1h.01" />
    </>
  ),
  gain: (
    <>
      <circle cx="10" cy="10" r="7.6" />
      <path d="M6.6 10.3l2.3 2.3 4.5-4.7" />
    </>
  ),
  warning: (
    <>
      <path d="M10 3.1 17.9 16.7H2.1z" />
      <path d="M10 8.4v3.2M10 14.1h.01" />
    </>
  ),
};

function NoticeIcon({ tone }: { tone: NoticeTone }) {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="mt-0.5 h-5 w-5 shrink-0"
    >
      {ICON[tone]}
    </svg>
  );
}

export default function StatusNotice({ role, tone, title, children }: StatusNoticeProps) {
  return (
    <div role={role} className={`flex gap-3 rounded-lg border px-4 py-3 ${SURFACE[tone]}`}>
      <NoticeIcon tone={tone} />
      <div className="flex min-w-0 flex-col gap-1">
        <p className="font-semibold text-balance">{title}</p>
        {children}
      </div>
    </div>
  );
}
