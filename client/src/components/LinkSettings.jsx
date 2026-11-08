import { useEffect, useId, useRef, useState } from 'react';
import { ArrowLeft, Check, Pencil } from 'lucide-react';
import Modal from './Modal';
import IconPicker from './IconPicker';
import LinkIcon from './LinkIcon';
import StackSelect from './StackSelect';
import { useDeleteLink, useLinkIcon, useUpdateLink } from '../hooks/useLinks';
import { urlError } from '../utils/links';

const ICON_UPDATED_NOTICE_MS = 4000;
const nameError = value => (value.trim() ? null : 'Enter a name');
const VALIDATORS = { name: nameError, url: value => urlError(value.trim()) };
const detailsOf = ({ name, url, stack }) => ({ name, url, stack });

const withoutSaved = (drafts, link) => {
  const unsaved = Object.entries(drafts).filter(([field, value]) => value !== link[field]);
  return unsaved.length === Object.keys(drafts).length ? drafts : Object.fromEntries(unsaved);
};

/** Runs after Modal restores focus to its opener, which may be gone or may have moved with its tile. */
const focusAfterDialog = (linkId) => {
  setTimeout(() => {
    const menu = document.querySelector(`[data-link-menu="${linkId}"]`);
    (menu ?? document.querySelector('button[aria-label="Settings"]'))?.focus();
  }, 0);
};

function DeleteSection({ link, onDeleted }) {
  const [confirming, setConfirming] = useState(false);
  const remove = useDeleteLink();
  const headingId = useId();
  const cancelRef = useRef(null);
  const deleteRef = useRef(null);
  const asked = useRef(false);
  // The button that had focus is replaced by the question, and the other way round.
  useEffect(() => {
    if (confirming) cancelRef.current?.focus();
    else if (asked.current) deleteRef.current?.focus();
    asked.current = confirming;
  }, [confirming]);
  const confirm = async () => {
    try {
      await remove.mutateAsync(link.id);
    } catch {
      return;
    }
    onDeleted();
  };
  return (
    <section className="settings-section" aria-labelledby={headingId}>
      <h3 id={headingId} className="settings-heading">Delete link</h3>
      {confirming ? (
        <div role="alertdialog" aria-label="Confirm delete" className="link-delete-confirm">
          <span>{`Delete ${link.name}? It's removed from the dashboard. Containers aren't affected.`}</span>
          <button ref={cancelRef} type="button" disabled={remove.isPending} onClick={() => setConfirming(false)}>Cancel</button>
          <button type="button" className="link-delete-yes" disabled={remove.isPending} onClick={confirm}>Delete</button>
        </div>
      ) : (
        <button ref={deleteRef} type="button" className="link-delete" onClick={() => setConfirming(true)}>Delete link</button>
      )}
      {remove.error && <p role="alert" className="selection-error">Couldn't delete the link. Please try again.</p>}
    </section>
  );
}

/** Edits save when leaving a field. `drafts` holds what's typed and not yet known to be saved, so a poll can't replace it. */
export default function LinkSettings({ link, stacks, initialAlert = null, onClose, onDeleted }) {
  const formId = useId();
  const update = useUpdateLink(link.id);
  const icon = useLinkIcon(link);
  const [drafts, setDrafts] = useState({});
  const [invalid, setInvalid] = useState({});
  const [choosingIcon, setChoosingIcon] = useState(false);
  const [iconUpdated, setIconUpdated] = useState(false);
  const iconButtonRef = useRef(null);
  const focusIconButton = useRef(false);
  const newStackDraft = useRef(null);
  // The values each save builds on: saves can follow each other faster than the list refreshes.
  const latest = useRef(detailsOf(link));
  const { name: savedName, url: savedUrl, stack: savedStack } = link;
  const saving = update.isPending;
  useEffect(() => {
    if (!saving) latest.current = { name: savedName, url: savedUrl, stack: savedStack };
  }, [savedName, savedUrl, savedStack, saving]);

  // A draft the server now agrees with is no longer unsaved, so later polls may update the field.
  const remaining = withoutSaved(drafts, link);
  if (remaining !== drafts) setDrafts(remaining);

  const linkId = link.id;
  useEffect(() => () => focusAfterDialog(linkId), [linkId]);

  useEffect(() => {
    if (!choosingIcon && focusIconButton.current) {
      focusIconButton.current = false;
      iconButtonRef.current?.focus();
    }
  }, [choosingIcon]);
  useEffect(() => {
    if (!iconUpdated) return undefined;
    const timer = setTimeout(() => setIconUpdated(false), ICON_UPDATED_NOTICE_MS);
    return () => clearTimeout(timer);
  }, [iconUpdated]);

  const save = (changes) => {
    const next = { ...latest.current, ...changes };
    latest.current = next;
    update.mutate(next);
  };
  const setDraft = (field, value) => setDrafts(current => ({ ...current, [field]: value }));

  const commit = (field) => {
    const value = drafts[field];
    if (value === undefined) return;
    const error = VALIDATORS[field](value);
    setInvalid(current => ({ ...current, [field]: Boolean(error) }));
    if (error) return;
    const trimmed = value.trim();
    setDraft(field, trimmed);
    if (trimmed !== latest.current[field]) save({ [field]: trimmed });
  };
  const changeField = (field, value) => {
    setDraft(field, value);
    if (invalid[field] && !VALIDATORS[field](value)) setInvalid(current => ({ ...current, [field]: false }));
  };
  const chooseStack = (stack) => {
    newStackDraft.current = null;
    setDraft('stack', stack);
    if (stack !== latest.current.stack) save({ stack });
  };

  /** Esc or Close may arrive while a field still has focus, so nothing has saved it yet. */
  const close = () => {
    const changes = {};
    ['name', 'url'].forEach((field) => {
      const value = drafts[field];
      if (value === undefined || VALIDATORS[field](value)) return;
      if (value.trim() !== latest.current[field]) changes[field] = value.trim();
    });
    const newStack = newStackDraft.current?.trim();
    if (newStack && newStack !== latest.current.stack) changes.stack = newStack;
    if (Object.keys(changes).length > 0) save(changes);
    onClose();
  };

  const closePicker = () => {
    focusIconButton.current = true;
    setChoosingIcon(false);
  };
  const location = link.stack ? `External / ${link.stack}` : 'External';
  const subtitle = `Link · ${location}`;

  if (choosingIcon) {
    return (
      <Modal
        title="Choose icon"
        icon={(
          <button type="button" className="modal-back" aria-label="Back to link settings" disabled={icon.isSaving} onClick={closePicker}>
            <ArrowLeft size={20} aria-hidden="true" />
          </button>
        )}
        subtitle={subtitle}
        onClose={close}
        className="settings-modal"
      >
        <IconPicker
          iconUrl={link.iconUrl}
          iconChoice={link.iconChoice}
          startingSearch={link.name}
          icon={icon}
          onSaved={() => { setIconUpdated(true); closePicker(); }}
        />
      </Modal>
    );
  }

  const fieldError = field => (invalid[field] ? VALIDATORS[field](drafts[field] ?? '') : null);
  const nameProblem = fieldError('name');
  const urlProblem = fieldError('url');
  return (
    <Modal
      title={link.name}
      icon={(
        <button
          ref={iconButtonRef}
          type="button"
          className="icon-edit"
          aria-label={`Change icon for ${link.name}`}
          title="Change icon"
          onClick={() => { setIconUpdated(false); setChoosingIcon(true); }}
        >
          <LinkIcon iconUrl={link.iconUrl} size={44} />
          <span className="icon-edit-pencil" aria-hidden="true"><Pencil size={9} strokeWidth={2.6} /></span>
        </button>
      )}
      subtitle={iconUpdated
        ? <output className="icon-updated"><Check size={12} strokeWidth={3} aria-hidden="true" />Icon updated</output>
        : subtitle}
      onClose={close}
      className="settings-modal"
    >
      {initialAlert && <p role="alert" className="selection-error">{initialAlert}</p>}

      <div className="settings-section link-field">
        <label htmlFor={`${formId}-name`} className="settings-heading">Name</label>
        <input
          id={`${formId}-name`}
          className="link-input"
          type="text"
          maxLength={255}
          value={drafts.name ?? link.name}
          aria-invalid={Boolean(nameProblem)}
          aria-describedby={nameProblem ? `${formId}-name-error` : undefined}
          onChange={event => changeField('name', event.target.value)}
          onBlur={() => commit('name')}
        />
        {nameProblem && <span id={`${formId}-name-error`} role="alert" className="selection-error">{nameProblem}</span>}
      </div>
      <div className="settings-divider" />

      <div className="settings-section link-field">
        <label htmlFor={`${formId}-url`} className="settings-heading">Tile opens</label>
        <input
          id={`${formId}-url`}
          className="link-input link-input--mono"
          type="url"
          inputMode="url"
          value={drafts.url ?? link.url}
          aria-invalid={Boolean(urlProblem)}
          aria-describedby={`${formId}-url-note`}
          onChange={event => changeField('url', event.target.value)}
          onBlur={() => commit('url')}
        />
        {urlProblem
          ? <span id={`${formId}-url-note`} role="alert" className="selection-error">{urlProblem}</span>
          : <span id={`${formId}-url-note`} className="link-hint">Starts with http:// or https://.</span>}
      </div>
      <div className="settings-divider" />

      <div className="settings-section link-field">
        <label htmlFor={`${formId}-stack`} className="settings-heading">Stack</label>
        <StackSelect
          id={`${formId}-stack`}
          value={'stack' in drafts ? drafts.stack : link.stack}
          options={stacks}
          onChange={chooseStack}
          onNewChange={(text) => { newStackDraft.current = text; }}
          onCommitNew={(name) => { newStackDraft.current = null; chooseStack(name); }}
        />
      </div>

      <p className="link-saving-note">
        <Check size={12} strokeWidth={3} aria-hidden="true" />
        Changes save when you leave a field. An invalid URL isn't saved.
      </p>
      {update.error && <p role="alert" className="selection-error">Couldn't save your change. Please try again.</p>}
      <div className="settings-divider" />

      <DeleteSection link={link} onDeleted={onDeleted} />
    </Modal>
  );
}
