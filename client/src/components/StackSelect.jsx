import { useEffect, useRef, useState } from 'react';
import { ChevronDown } from 'lucide-react';

const NONE = 'none';
const NEW = 'new';
// Prefixed so a stack can have any name, even one like the special options.
const optionValue = name => `s:${name}`;

/**
 * Picks an existing stack, none, or a new one.
 * `onNewChange` reports each keystroke of a new name and `onCommitNew` the trimmed name on leaving the input.
 */
export default function StackSelect({ id, value, options, ariaLabel, describedBy, onChange, onNewChange, onCommitNew }) {
  const [adding, setAdding] = useState(false);
  const [text, setText] = useState('');
  const [committed, setCommitted] = useState(null);
  const inputRef = useRef(null);

  if (adding && committed !== null && value === committed) {
    setAdding(false);
    setText('');
    setCommitted(null);
  }

  const openedInput = useRef(false);
  useEffect(() => {
    if (adding && openedInput.current) inputRef.current?.focus();
    openedInput.current = false;
  }, [adding]);

  const names = adding || value === null || options.includes(value) ? options : [...options, value].sort((a, b) => a.localeCompare(b));
  let selected = value === null ? NONE : optionValue(value);
  if (adding) selected = NEW;

  const choose = (event) => {
    const choice = event.target.value;
    if (choice === NEW) {
      openedInput.current = true;
      setAdding(true);
      setText('');
      setCommitted(null);
      onNewChange?.('');
      return;
    }
    setAdding(false);
    onChange(choice === NONE ? null : choice.slice(optionValue('').length));
  };

  const commit = () => {
    const name = text.trim();
    if (!name) return;
    setCommitted(name);
    onCommitNew?.(name);
  };

  return (
    <>
      <span className="link-select">
        <select id={id} className="link-input" aria-label={ariaLabel} aria-describedby={describedBy} value={selected} onChange={choose}>
          <option value={NONE}>No stack</option>
          {names.map(name => <option key={name} value={optionValue(name)}>{name}</option>)}
          <option value={NEW}>New stack…</option>
        </select>
        <ChevronDown size={16} aria-hidden="true" />
      </span>
      {adding && (
        <input
          ref={inputRef}
          type="text"
          className="link-input"
          aria-label="New stack name"
          value={text}
          onChange={event => { setText(event.target.value); onNewChange?.(event.target.value); }}
          onBlur={commit}
        />
      )}
    </>
  );
}
