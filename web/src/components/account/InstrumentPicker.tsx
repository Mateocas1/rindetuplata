/**
 * Searchable instrument list for an alert rule (T7d).
 *
 * A plain search input plus a list of buttons, not a fake combobox: the ARIA
 * combobox pattern brings a key map and `aria-activedescendant` this list does
 * not need, and native buttons keep the keyboard path intact. The result count
 * is announced politely, and the chosen option collapses to a chip with a way
 * back ("Quitar"), which uses the one inline-link recipe of the system (T12c).
 */
import { useMemo, useState } from 'react';
import type { InstrumentSummary } from '../../lib/account';
import { instrumentOptionLabel } from '../../lib/accountForm';
import { formatInteger } from '../../lib/format';
import { inputClass } from './ui';

const MAX_RESULTS = 8;

export interface InstrumentPickerProps {
  id: string;
  instruments: InstrumentSummary[];
  selectedId: number | null;
  onSelect: (id: number | null) => void;
  describedBy?: string;
}

export default function InstrumentPicker({
  id,
  instruments,
  selectedId,
  onSelect,
  describedBy,
}: InstrumentPickerProps) {
  const [query, setQuery] = useState('');

  const matches = useMemo(() => {
    const needle = query.trim().toLowerCase();
    const list =
      needle === ''
        ? instruments
        : instruments.filter((instrument) =>
            instrumentOptionLabel(instrument).toLowerCase().includes(needle),
          );
    return list.slice(0, MAX_RESULTS);
  }, [instruments, query]);

  const selected = selectedId === null ? undefined : instruments.find((i) => i.id === String(selectedId));

  if (selectedId !== null) {
    return (
      <div className="flex flex-wrap items-center gap-2">
        <span className="rounded-md border border-border bg-subtle-surface px-3 py-2 text-body-sm">
          {selected === undefined ? `Opción #${selectedId}` : instrumentOptionLabel(selected)}
        </span>
        <button
          type="button"
          onClick={() => onSelect(null)}
          className="link rounded-sm px-2 py-2 text-body-sm"
        >
          Quitar
        </button>
      </div>
    );
  }

  const hintId = `${id}-ayuda`;
  const statusId = `${id}-resultados`;
  const describedIds = [hintId, statusId, describedBy].filter(
    (value): value is string => value !== undefined,
  );

  return (
    <div className="flex flex-col gap-2">
      <input
        id={id}
        name="instrumento"
        type="search"
        autoComplete="off"
        value={query}
        aria-describedby={describedIds.join(' ')}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Buscar por nombre, banco o fondo"
        className={`${inputClass} max-w-none`}
      />
      <p id={hintId} className="text-caption text-muted">
        Escribí para buscar entre {formatInteger(instruments.length)} opciones. Si no elegís
        ninguna, la alerta mira todas las de ese tipo.
      </p>
      <p id={statusId} role="status" className="text-caption text-muted">
        {matches.length === 0
          ? 'Sin coincidencias.'
          : `Mostrando ${formatInteger(matches.length)} de ${formatInteger(instruments.length)} opciones.`}
      </p>
      {matches.length > 0 && (
        <ul className="flex max-h-64 flex-col gap-1 overflow-y-auto overscroll-contain rounded-md border border-border bg-surface-raised p-2">
          {matches.map((instrument) => (
            <li key={instrument.id}>
              <button
                type="button"
                onClick={() => {
                  onSelect(Number(instrument.id));
                  setQuery('');
                }}
                className="w-full rounded-sm px-2 py-2 text-start text-body-sm hover:bg-subtle-surface"
              >
                {instrumentOptionLabel(instrument)}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
