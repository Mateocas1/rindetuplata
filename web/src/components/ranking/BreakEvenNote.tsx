/**
 * The dólar MEP break-even of one entry (S5, L24 b), worded by `breakEvenNote` from the API's own numbers. The same
 * two lines serve the ranking row (inside its disclosure) and the simulator result; nothing renders without a
 * stored quote, and the lines are plain text, so their accessible name is what is on screen.
 */
import { breakEvenNote } from '../../lib/breakEven';
import type { BreakEvenMep, MepQuote } from '../../lib/types';

export interface BreakEvenNoteProps {
  breakEven: BreakEvenMep | null | undefined;
  quote: MepQuote | null | undefined;
  days: number;
}

export default function BreakEvenNote({ breakEven, quote, days }: BreakEvenNoteProps) {
  const note = breakEvenNote(breakEven, quote, days);
  if (note === null) {
    return null;
  }
  return (
    <>
      <p className="type-data">
        {note.rule} ({note.quote}).
      </p>
      <p className="type-data text-muted">{note.price}</p>
    </>
  );
}
