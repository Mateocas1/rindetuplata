/**
 * The create-key form of `/claves-api/`.
 *
 * One optional label and the view's one primary action. The panel holds the state and the request; this form renders
 * the card. When the three slots are taken the button is disabled and the sentence next to it says why, so the cap is
 * never a silent refusal. Help and error text stay tied to the input with `aria-describedby`.
 */
import { MAX_NAME_LENGTH, keySlotsText } from '../../lib/apiKeys';
import {
  buttonPrimaryClass,
  cardClass,
  errorTextClass,
  fieldGroupClass,
  labelClass,
} from '../account/ui';

const NAME_INPUT_ID = 'clave-nombre';
const NAME_HINT_ID = 'clave-nombre-ayuda';
const NAME_ERROR_ID = 'clave-nombre-error';

export interface ApiKeyCreateFormProps {
  name: string;
  error: string | null;
  activeCount: number;
  canCreate: boolean;
  creating: boolean;
  onChange: (name: string) => void;
  onSubmit: (event: { preventDefault: () => void }) => void;
}

export default function ApiKeyCreateForm({
  name,
  error,
  activeCount,
  canCreate,
  creating,
  onChange,
  onSubmit,
}: ApiKeyCreateFormProps) {
  return (
    <form onSubmit={onSubmit} noValidate className={`${cardClass} flex flex-col gap-block`}>
      <div className={fieldGroupClass}>
        <label htmlFor={NAME_INPUT_ID} className={labelClass}>
          Nombre (opcional)
        </label>
        <input
          id={NAME_INPUT_ID}
          name="nombre"
          autoComplete="off"
          value={name}
          aria-invalid={error !== null}
          aria-describedby={error === null ? NAME_HINT_ID : `${NAME_HINT_ID} ${NAME_ERROR_ID}`}
          onChange={(event) => onChange(event.target.value)}
          className="field"
        />
        <p id={NAME_HINT_ID} className="type-body text-muted">
          Para reconocerla después, por ejemplo el nombre del script que la usa. Hasta {MAX_NAME_LENGTH}{' '}
          caracteres.
        </p>
        {error !== null && (
          <p id={NAME_ERROR_ID} className={errorTextClass}>
            {error}
          </p>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <button
          type="submit"
          disabled={creating || !canCreate}
          className={`${buttonPrimaryClass} self-start`}
        >
          {creating ? 'Creando…' : 'Crear clave'}
        </button>
        <p className="type-body text-muted" role="status">
          {keySlotsText(activeCount)}
        </p>
      </div>
    </form>
  );
}
