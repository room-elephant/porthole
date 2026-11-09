import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import AddLink from '../../components/AddLink';
import { createLink, fetchIconLibrary, selectEntryIcon, uploadEntryIcon } from '../../api';
import { renderWithQuery } from '../testUtils';

vi.mock('../../api', () => ({
  createLink: vi.fn(),
  fetchIconLibrary: vi.fn(),
  selectEntryIcon: vi.fn(),
  uploadEntryIcon: vi.fn(),
}));

const ID = 'a'.repeat(32);
const name = () => screen.getByRole('textbox', { name: 'Name' });
const url = () => screen.getByLabelText('URL');
const stack = () => screen.getByRole('combobox', { name: /Stack/ });
const submit = () => screen.getByRole('button', { name: 'Add link' });
const iconButton = () => screen.getByRole('button', { name: 'Choose icon for this link' });
const png = new File(['x'], 'logo.png', { type: 'image/png' });

const setup = () => {
  const props = { stacks: ['base', 'home'], onCancel: vi.fn(), onClose: vi.fn(), onAdded: vi.fn(), onIconFailed: vi.fn() };
  return { ...renderWithQuery(<AddLink {...props} />), ...props };
};
const fill = (values = {}) => {
  fireEvent.change(name(), { target: { value: values.name ?? 'Router' } });
  fireEvent.change(url(), { target: { value: values.url ?? 'http://192.168.50.1/' } });
};

beforeEach(() => {
  vi.resetAllMocks();
  HTMLDialogElement.prototype.showModal ??= vi.fn();
  HTMLDialogElement.prototype.close ??= vi.fn();
  createLink.mockResolvedValue({ id: ID });
  selectEntryIcon.mockResolvedValue();
  uploadEntryIcon.mockResolvedValue();
  fetchIconLibrary.mockResolvedValue({ router: {}, coredns: {} });
  URL.createObjectURL = vi.fn(() => 'blob:preview');
  URL.revokeObjectURL = vi.fn();
});

afterEach(() => { delete URL.createObjectURL; delete URL.revokeObjectURL; });

describe('AddLink validation', () => {
  it('follows the board', () => {
    setup();
    expect(screen.getByRole('heading', { name: 'Add link' })).toBeInTheDocument();
    expect(name()).toHaveFocus();
    expect(screen.getByText('The page the tile opens. Starts with http:// or https://.')).toBeInTheDocument();
    expect(screen.getByText('In Stack view it sits next to the containers of that stack, from any host.')).toBeInTheDocument();
  });

  it('shows errors and sends nothing while invalid', () => {
    setup();
    fireEvent.change(url(), { target: { value: '192.168.50.1' } });
    fireEvent.click(submit());
    expect(screen.getByText('Enter a name')).toBeInTheDocument();
    expect(screen.getByText('Add http:// or https:// at the start.')).toBeInTheDocument();
    expect(name()).toHaveAttribute('aria-invalid', 'true');
    expect(url()).toHaveAttribute('aria-invalid', 'true');
    expect(name()).toHaveFocus();
    expect(createLink).not.toHaveBeenCalled();
  });

  it('shows no errors before the first submit', () => {
    setup();
    fireEvent.change(url(), { target: { value: 'nope' } });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('clears an error as the value becomes valid', () => {
    setup();
    fireEvent.click(submit());
    fill();
    expect(screen.queryByText('Enter a name')).not.toBeInTheDocument();
    expect(screen.queryByText('Add http:// or https:// at the start.')).not.toBeInTheDocument();
  });
});

describe('AddLink saving', () => {
  it('creates the link, trimmed, then reports back', async () => {
    const { onAdded } = setup();
    fill({ name: ' Router ', url: ' http://192.168.50.1/ ' });
    fireEvent.change(stack(), { target: { value: 's:base' } });
    fireEvent.click(submit());
    await waitFor(() => expect(onAdded).toHaveBeenCalled());
    expect(createLink).toHaveBeenCalledWith({ name: 'Router', url: 'http://192.168.50.1/', stack: 'base' });
    expect(selectEntryIcon).not.toHaveBeenCalled();
  });

  it('sends no stack as null', async () => {
    const { onAdded } = setup();
    fill();
    fireEvent.click(submit());
    await waitFor(() => expect(onAdded).toHaveBeenCalled());
    expect(createLink.mock.calls[0][0].stack).toBeNull();
  });

  it('sends a typed new stack, even when submitted without leaving the field', async () => {
    const { onAdded } = setup();
    fill();
    fireEvent.change(stack(), { target: { value: 'new' } });
    fireEvent.change(screen.getByRole('textbox', { name: 'New stack name' }), { target: { value: ' media ' } });
    fireEvent.submit(submit().closest('form'));
    await waitFor(() => expect(onAdded).toHaveBeenCalled());
    expect(createLink.mock.calls[0][0].stack).toBe('media');
  });

  it('keeps the values and shows an alert when creating fails, with no icon request', async () => {
    createLink.mockRejectedValue(new Error('down'));
    const { onAdded } = setup();
    fill();
    await pickLibraryIcon();
    fireEvent.click(submit());
    expect(await screen.findByRole('alert')).toHaveTextContent("Couldn't add the link.");
    expect(name()).toHaveValue('Router');
    expect(selectEntryIcon).not.toHaveBeenCalled();
    expect(onAdded).not.toHaveBeenCalled();
  });

  it('explains the link limit', async () => {
    createLink.mockRejectedValue({ response: { status: 507 } });
    setup();
    fill();
    fireEvent.click(submit());
    expect(await screen.findByRole('alert')).toHaveTextContent('Porthole has reached its limit of 500 links.');
  });

  it('does not submit twice while saving', async () => {
    let finish;
    createLink.mockReturnValue(new Promise(done => { finish = done; }));
    const { onAdded } = setup();
    fill();
    fireEvent.click(submit());
    await waitFor(() => expect(createLink).toHaveBeenCalled());
    fireEvent.submit(submit().closest('form'));
    await act(async () => { finish({ id: ID }); });
    await waitFor(() => expect(onAdded).toHaveBeenCalled());
    expect(createLink).toHaveBeenCalledTimes(1);
  });

  it('cancels', () => {
    const { onCancel } = setup();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancel).toHaveBeenCalled();
  });
});

const pickLibraryIcon = async () => {
  fireEvent.click(iconButton());
  fireEvent.click(await screen.findByRole('button', { name: 'router' }));
  await screen.findByRole('heading', { name: 'Add link' });
};
const chooseFile = async () => {
  fireEvent.click(iconButton());
  fireEvent.click(screen.getByRole('tab', { name: 'Upload' }));
  const input = document.querySelector('input[type="file"]');
  fireEvent.change(input, { target: { files: [png] } });
  await screen.findByRole('heading', { name: 'Add link' });
};

describe('AddLink icon', () => {
  it('holds a library pick until submit, then sends it with the link', async () => {
    const { onAdded } = setup();
    fill();
    await pickLibraryIcon();
    expect(createLink).not.toHaveBeenCalled();
    expect(iconButton().querySelector('img').src).toMatch(/router\.webp$/);

    fireEvent.click(submit());
    await waitFor(() => expect(onAdded).toHaveBeenCalled());
    expect(createLink).toHaveBeenCalledWith({
      name: 'Router', url: 'http://192.168.50.1/', stack: null, icon: { library: 'router' },
    });
    expect(selectEntryIcon).not.toHaveBeenCalled();
  });

  it('holds a file until the link exists, then uploads it', async () => {
    const { onAdded } = setup();
    fill();
    await chooseFile();
    expect(uploadEntryIcon).not.toHaveBeenCalled();
    expect(iconButton().querySelector('img')).toHaveAttribute('src', 'blob:preview');

    fireEvent.click(submit());
    await waitFor(() => expect(onAdded).toHaveBeenCalled());
    expect(uploadEntryIcon).toHaveBeenCalledWith({ entryId: ID, file: png });
  });

  it('reports a failed upload with the new link, not as added', async () => {
    uploadEntryIcon.mockRejectedValue(new Error('down'));
    const { onAdded, onIconFailed } = setup();
    fill();
    await chooseFile();
    fireEvent.click(submit());
    await waitFor(() => expect(onIconFailed).toHaveBeenCalledWith(ID));
    expect(onAdded).not.toHaveBeenCalled();
  });

  it('starts library search from the Name field', async () => {
    setup();
    fill({ name: 'Router' });
    fireEvent.click(iconButton());
    expect(screen.getByRole('searchbox', { name: 'Search icons' })).toHaveValue('Router');
    await screen.findByRole('button', { name: 'router' });
  });

  it('keeps the values when going back from the picker, returning focus to the icon button', async () => {
    setup();
    fill();
    fireEvent.click(iconButton());
    fireEvent.click(screen.getByRole('button', { name: 'Back to add link' }));
    expect(name()).toHaveValue('Router');
    expect(url()).toHaveValue('http://192.168.50.1/');
    await waitFor(() => expect(iconButton()).toHaveFocus());
    await act(async () => {});
  });

  it('revokes a replaced preview and the last one on unmount', async () => {
    const { unmount } = setup();
    fill();
    await chooseFile();
    await pickLibraryIcon();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview');
    URL.revokeObjectURL.mockClear();
    await chooseFile();
    unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview');
  });
});
