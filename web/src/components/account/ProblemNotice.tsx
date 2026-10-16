/**
 * The one failure box of the account pages (T7d, T12c): an urgent, untied
 * failure announced with `role="alert"`, in Spanish, never the API's English
 * `detail`.
 *
 * The tone, the icon and the surface come from `StatusNotice`; the retry is a
 * peer action (`btn-secondary`) even though it is the only button here, so a
 * view keeps its one primary for its own main action.
 */
import type { ReactNode } from 'react';
import { problemText } from '../../lib/accountStatus';
import StatusNotice from './StatusNotice';
import { buttonSecondaryClass } from './ui';

export interface ProblemNoticeProps {
  problem: { status: number; message: string };
  title?: string;
  onRetry?: () => void;
  children?: ReactNode;
}

export default function ProblemNotice({
  problem,
  title = 'No pudimos completar la operación',
  onRetry,
  children,
}: ProblemNoticeProps) {
  return (
    <StatusNotice role="alert" tone="loss" title={title}>
      <p className="type-body">{problemText(problem)}</p>
      {children}
      {onRetry !== undefined && (
        <button
          type="button"
          onClick={onRetry}
          className={`${buttonSecondaryClass} mt-1 self-start`}
        >
          Reintentar
        </button>
      )}
    </StatusNotice>
  );
}
