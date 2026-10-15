/**
 * The one error box of the account pages (T7d): an urgent, untied failure
 * announced with `role="alert"`, in Spanish, never the API's English `detail`.
 */
import type { ReactNode } from 'react';
import { problemText } from '../../lib/accountStatus';

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
    <div
      role="alert"
      className="flex flex-col gap-2 rounded-lg border border-loss bg-loss-surface px-4 py-3"
    >
      <p className="font-semibold text-on-loss">{title}</p>
      <p className="text-body-sm text-on-loss">{problemText(problem)}</p>
      {children}
      {onRetry !== undefined && (
        <button
          type="button"
          onClick={onRetry}
          className="self-start rounded-md bg-accent-solid px-4 py-2 text-body-sm font-semibold text-on-accent hover:bg-accent"
        >
          Reintentar
        </button>
      )}
    </div>
  );
}
