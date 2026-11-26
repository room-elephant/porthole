import { useEffect, useId, useRef, useState } from 'react';
import { ArrowLeft, Pencil } from 'lucide-react';
import Modal from './Modal';
import IconPicker from './IconPicker';
import LinkIcon from './LinkIcon';
import StackSelect from './StackSelect';
import { useCreateLink, useSetLinkIcon } from '../hooks/useLinks';
import { libraryIconUrl } from '../utils/icons';
import { urlError } from '../utils/links';

const SUBTITLE_DETAIL = " · a page Porthole doesn't run, like your router";

/** Keeps a chosen icon until the link exists. Has the shape IconPicker expects. */
const useHeldIcon = () => {
  const [held, setHeld] = useState(null);
  const previewUrl = useRef(null);
  const release = () => {
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
    previewUrl.current = null;
  };
  useEffect(() => release, []);
  const noop = () => {};
  return {
    held,
    icon: {
      selectIcon: (name, { onSaved } = {}) => {
        release();
        setHeld({ name, iconUrl: libraryIconUrl(name) });
        onSaved?.();
      },
      uploadIcon: (file, { onSaved } = {}) => {
        release();
        previewUrl.current = URL.createObjectURL(file);
        setHeld({ file, iconUrl: previewUrl.current });
        onSaved?.();
      },
      retry: noop,
      clearError: noop,
      isSaving: false,
      saveError: null,
    },
  };
};

const createErrorMessage = error => (error.response?.status === 507
  ? 'Porthole has reached its limit of 500 links.'
  : "Couldn't add the link. Please try again.");

export default function AddLink({ stacks, onCancel, onClose, onAdded, onIconFailed }) {
  const formId = useId();
  const [name, setName] = useState('');
  const [url, setUrl] = useState('');
  const [stack, setStack] = useState(null);
  const [submitted, setSubmitted] = useState(false);
  const [choosingIcon, setChoosingIcon] = useState(false);
  const { held, icon } = useHeldIcon();
  const create = useCreateLink();
  const setLinkIcon = useSetLinkIcon();
  const nameRef = useRef(null);
  const urlRef = useRef(null);
  const iconButtonRef = useRef(null);
  const focusIconButton = useRef(false);

  useEffect(() => {
    if (choosingIcon) return;
    if (focusIconButton.current) {
      focusIconButton.current = false;
      iconButtonRef.current?.focus();
    } else {
      nameRef.current?.focus();
    }
  }, [choosingIcon]);

  const nameError = name.trim() ? null : 'Enter a name';
  const addressError = urlError(url.trim());
  const closePicker = () => {
    focusIconButton.current = true;
    setChoosingIcon(false);
  };

  const submit = async (event) => {
    event.preventDefault();
    setSubmitted(true);
    if (nameError || addressError) {
      (nameError ? nameRef : urlRef).current?.focus();
      return;
    }
    if (create.isPending || setLinkIcon.isPending) return;
    // A library icon goes with the link; a file can only be uploaded once the link exists.
    const details = { name: name.trim(), url: url.trim(), stack: stack?.trim() || null };
    if (held?.name) details.icon = { library: held.name };
    let link;
    try {
      link = await create.mutateAsync(details);
    } catch {
      return;
    }
    if (held?.file) {
      try {
        await setLinkIcon.mutateAsync({ id: link.id, file: held.file });
      } catch {
        onIconFailed(link.id);
        return;
      }
    }
    onAdded();
  };

  if (choosingIcon) {
    return (
      <Modal
        title="Choose icon"
        icon={(
          <button type="button" className="modal-back" aria-label="Back to add link" onClick={closePicker}>
            <ArrowLeft size={20} aria-hidden="true" />
          </button>
        )}
        subtitle="Add link"
        onClose={onClose}
        className="settings-modal"
      >
        <IconPicker iconUrl={held?.iconUrl ?? null} startingSearch={name.trim()} icon={icon} onSaved={closePicker} />
      </Modal>
    );
  }

  const showError = error => submitted && error;
  const saving = create.isPending || setLinkIcon.isPending;
  return (
    <Modal
      title="Add link"
      icon={(
        <button ref={iconButtonRef} type="button" className="icon-edit" aria-label="Choose icon for this link" title="Choose icon" onClick={() => setChoosingIcon(true)}>
          <LinkIcon iconUrl={held?.iconUrl ?? null} size={44} badge={false} />
          <span className="icon-edit-pencil" aria-hidden="true"><Pencil size={9} strokeWidth={2.6} /></span>
        </button>
      )}
      subtitle={<>External<span className="link-subtitle-detail">{SUBTITLE_DETAIL}</span></>}
      onClose={onClose}
      className="settings-modal"
    >
      <form className="link-form" onSubmit={submit} noValidate aria-busy={saving}>
        <div className="link-field">
          <label htmlFor={`${formId}-name`} className="link-label">Name</label>
          <input
            ref={nameRef}
            id={`${formId}-name`}
            className="link-input"
            type="text"
            maxLength={255}
            value={name}
            aria-invalid={Boolean(showError(nameError))}
            aria-describedby={showError(nameError) ? `${formId}-name-error` : undefined}
            onChange={event => setName(event.target.value)}
          />
          {showError(nameError) && <span id={`${formId}-name-error`} role="alert" className="selection-error">{nameError}</span>}
        </div>

        <div className="link-field">
          <label htmlFor={`${formId}-url`} className="link-label">URL</label>
          <input
            ref={urlRef}
            id={`${formId}-url`}
            className="link-input link-input--mono"
            type="url"
            inputMode="url"
            autoComplete="url"
            value={url}
            aria-invalid={Boolean(showError(addressError))}
            aria-describedby={`${formId}-url-note`}
            onChange={event => setUrl(event.target.value)}
          />
          {showError(addressError)
            ? <span id={`${formId}-url-note`} role="alert" className="selection-error">{addressError}</span>
            : <span id={`${formId}-url-note`} className="link-hint">The page the tile opens. Starts with http:// or https://.</span>}
        </div>

        <div className="link-field">
          <label htmlFor={`${formId}-stack`} className="link-label">Stack <span className="link-label-note">· optional</span></label>
          <StackSelect id={`${formId}-stack`} value={stack} options={stacks} describedBy={`${formId}-stack-hint`} onChange={setStack} onNewChange={setStack} />
          <span id={`${formId}-stack-hint`} className="link-hint">In Stack view it sits next to the containers of that stack, from any host.</span>
        </div>

        <span className="link-hint link-icon-hint">Icon: tap the icon at the top to choose one.</span>

        {create.error && <p role="alert" className="selection-error">{createErrorMessage(create.error)}</p>}

        <div className="link-actions">
          <button type="button" className="link-cancel" onClick={onCancel}>Cancel</button>
          <button type="submit" className="link-submit">Add link</button>
        </div>
      </form>
    </Modal>
  );
}
