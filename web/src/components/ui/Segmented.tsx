/**
 * Capsule segmented control (T13b, the Cobalto Cristal signature device).
 *
 * A real radio group: one `input[type=radio]` per option inside a `fieldset`
 * whose `legend` names the group, so keyboard arrow keys move the selection and
 * assistive technology announces an option list, not a row of buttons. The
 * track is the inset step of the surface ladder (`bg-subtle-surface`); the
 * selected option is painted with the `thumb` role (`bg-thumb` /
 * `text-on-thumb`), which is a shape and a contrast step at once, so the
 * selection survives reduced motion and a grayscale screenshot.
 *
 * The thumb is one absolutely positioned element that slides with `transform`;
 * the movement is gated by `motion-safe`. React never animates a number the
 * reader has to read, and the selected value itself is text in the thumb.
 *
 * The segment shows the preset's short form (T13e: at 320/390 the full amount
 * touched the thumb's edges) and adds the full value as screen-reader-only label
 * text, so the accessible name starts with what is visible ("$ 1M ($ 1.000.000)",
 * WCAG 2.5.3 label in name) and still states the exact amount.
 */
import type { Preset } from '../ranking/presets';

export interface SegmentedProps {
  /** The radio group name; unique on the page and never the name of a text input. */
  name: string;
  legend: string;
  options: Preset[];
  /** The selected option's value, or null when the reader's own value is off-preset. */
  selected: string | null;
  onSelect: (value: string) => void;
}

export default function Segmented({ name, legend, options, selected, onSelect }: SegmentedProps) {
  const selectedIndex = options.findIndex((option) => option.value === selected);

  return (
    <fieldset className="min-w-0 border-0 p-0">
      <legend className="type-label mb-2">{legend}</legend>
      <div className="relative grid auto-cols-fr grid-flow-col rounded-pill border border-border-strong bg-subtle-surface p-[3px]">
        {selectedIndex >= 0 && (
          <span
            aria-hidden="true"
            className="absolute inset-y-[3px] start-[3px] rounded-pill bg-thumb motion-safe:transition-transform motion-safe:duration-200 motion-safe:ease-standard"
            style={{
              inlineSize: `calc((100% - 6px) / ${options.length})`,
              transform: `translateX(${selectedIndex * 100}%)`,
            }}
          />
        )}
        {options.map((option) => (
          <label
            key={option.value}
            className={`type-data relative z-10 flex min-h-10 cursor-pointer items-center justify-center rounded-pill px-3 whitespace-nowrap has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus ${
              option.value === selected ? 'text-on-thumb' : 'text-muted hover:text-text'
            }`}
          >
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={option.value === selected}
              onChange={() => onSelect(option.value)}
              className="absolute size-px opacity-0"
            />
            {option.shortLabel}
            {option.shortLabel !== option.label && <span className="sr-only"> ({option.label})</span>}
          </label>
        ))}
      </div>
    </fieldset>
  );
}
