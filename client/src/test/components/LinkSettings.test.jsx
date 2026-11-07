import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import LinkSettings from '../../components/LinkSettings';
import { deleteLink, fetchIconLibrary, selectLinkIcon, updateLink } from '../../api';

vi.mock('../../api', () => ({
  deleteLink: vi.fn(),
  fetchIconLibrary: vi.fn(),
  selectLinkIcon: vi.fn(),
  updateLink: vi.fn(),
  uploadLinkIcon: vi.fn(),
}));

const link = { id: 'a'.repeat(32), name: 'Router', url: 'http://192.168.50.1/', stack: 'base', iconUrl: null };
const details = { name: 'Router', url: 'http://192.168.50.1/', stack: 'base' };
const nameField = () => screen.getByRole('textbox', { name: 'Name' });
const urlField = () => screen.getByLabelText('Tile opens');
const stackField = () => screen.getByRole('combobox', { name: 'Stack' });

const setup = (props = {}) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const handlers = { onClose: vi.fn(), onDeleted: vi.fn() };
  const ui = next => (
    <QueryClientProvider client={client}>
      <LinkSettings link={link} stacks={['base', 'home']} {...handlers} {...props} {...next} />
    </QueryClientProvider>
  );
  const view = render(ui());
  return { ...view, ...handlers, update: next => view.rerender(ui(next)) };
};
const edit = (field, value) => {
  fireEvent.change(field, { target: { value } });
  fireEvent.blur(field);
};

beforeEach(() => {
  vi.resetAllMocks();
  updateLink.mockResolvedValue();
  deleteLink.mockResolvedValue();
  selectLinkIcon.mockResolvedValue();
  fetchIconLibrary.mockResolvedValue({ router: {} });
});

describe('LinkSettings header', () => {
  it('shows the name, and External with the stack', () => {
    setup();
    expect(screen.getByRole('heading', { name: 'Router' })).toBeInTheDocument();
    expect(screen.getByText('Link · External / base')).toBeInTheDocument();
  });

  it('omits a missing stack', () => {
    setup({ link: { ...link, stack: null } });
    expect(screen.getByText('Link · External')).toBeInTheDocument();
  });

  it('shows the alert it was opened with', () => {
    setup({ initialAlert: "Link added, but its icon couldn't be saved." });
    expect(screen.getByRole('alert')).toHaveTextContent("Link added, but its icon couldn't be saved.");
  });

  it('does not show the reader note from the board', () => {
    setup();
    expect(screen.queryByText(/Shown after pressing/)).not.toBeInTheDocument();
    expect(screen.getByText("Changes save when you leave a field. An invalid URL isn't saved.")).toBeInTheDocument();
  });
});

describe('LinkSettings saving', () => {
  it('saves all three fields, trimmed, when a changed field loses focus', async () => {
    setup();
    edit(nameField(), ' Gateway ');
    await waitFor(() => expect(updateLink).toHaveBeenCalledWith(link.id, { ...details, name: 'Gateway' }));
  });

  it('saves nothing when a field loses focus unchanged', () => {
    setup();
    fireEvent.blur(nameField());
    fireEvent.blur(urlField());
    edit(nameField(), ' Router ');
    expect(updateLink).not.toHaveBeenCalled();
  });

  it('carries an earlier save into the next one before the list refreshes', async () => {
    setup();
    edit(nameField(), 'Gateway');
    edit(urlField(), 'http://10.0.0.1/');
    await waitFor(() => expect(updateLink).toHaveBeenCalledTimes(2));
    expect(updateLink).toHaveBeenLastCalledWith(link.id, { name: 'Gateway', url: 'http://10.0.0.1/', stack: 'base' });
  });

  it('does not save an empty name, keeping what was typed out of the tile', () => {
    setup();
    edit(nameField(), ' ');
    expect(screen.getByText('Enter a name')).toBeInTheDocument();
    expect(updateLink).not.toHaveBeenCalled();
  });

  it('does not save an invalid URL and keeps the typed value', () => {
    setup();
    edit(urlField(), '192.168.50.1');
    expect(screen.getByText('Add http:// or https:// at the start.')).toBeInTheDocument();
    expect(urlField()).toHaveValue('192.168.50.1');
    expect(updateLink).not.toHaveBeenCalled();
  });

  it('saves the stack dropdown on change, and none as null', async () => {
    setup();
    fireEvent.change(stackField(), { target: { value: 's:home' } });
    await waitFor(() => expect(updateLink).toHaveBeenLastCalledWith(link.id, { ...details, stack: 'home' }));
    expect(stackField().selectedOptions[0]).toHaveTextContent('home');
    fireEvent.change(stackField(), { target: { value: 'none' } });
    await waitFor(() => expect(updateLink).toHaveBeenLastCalledWith(link.id, { ...details, stack: null }));
  });

  it('saves a new stack when its input loses focus', async () => {
    setup();
    fireEvent.change(stackField(), { target: { value: 'new' } });
    edit(screen.getByRole('textbox', { name: 'New stack name' }), 'media');
    await waitFor(() => expect(updateLink).toHaveBeenCalledWith(link.id, { ...details, stack: 'media' }));
  });

  it('shows an alert and keeps the typed value when a save fails', async () => {
    updateLink.mockRejectedValue(new Error('down'));
    setup();
    edit(nameField(), 'Gateway');
    expect(await screen.findByRole('alert')).toHaveTextContent("Couldn't save your change.");
    expect(nameField()).toHaveValue('Gateway');
    expect(updateLink).toHaveBeenCalledTimes(1);
  });
});

describe('LinkSettings closing with unsaved edits', () => {
  it('saves a valid change when Esc closes before leaving the field', async () => {
    const { onClose } = setup();
    fireEvent.change(nameField(), { target: { value: 'Gateway' } });
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
    await waitFor(() => expect(updateLink).toHaveBeenCalledWith(link.id, { ...details, name: 'Gateway' }));
  });

  it('saves a typed new stack on closing', async () => {
    setup();
    fireEvent.change(stackField(), { target: { value: 'new' } });
    fireEvent.change(screen.getByRole('textbox', { name: 'New stack name' }), { target: { value: 'media' } });
    fireEvent.keyDown(document, { key: 'Escape' });
    await waitFor(() => expect(updateLink).toHaveBeenCalledWith(link.id, { ...details, stack: 'media' }));
  });

  it('closes without saving an invalid value, while saving the valid one', async () => {
    const { onClose } = setup();
    fireEvent.change(urlField(), { target: { value: 'nope' } });
    fireEvent.change(nameField(), { target: { value: 'Gateway' } });
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(onClose).toHaveBeenCalled();
    await waitFor(() => expect(updateLink).toHaveBeenCalledWith(link.id, { ...details, name: 'Gateway' }));
  });

  it('saves nothing on closing when nothing changed', () => {
    const { onClose } = setup();
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(onClose).toHaveBeenCalled();
    expect(updateLink).not.toHaveBeenCalled();
  });
});

describe('LinkSettings refreshes', () => {
  it('keeps unsaved typing when a poll brings a different value', () => {
    const { update } = setup();
    fireEvent.change(nameField(), { target: { value: 'Typing' } });
    update({ link: { ...link, name: 'Elsewhere' } });
    expect(nameField()).toHaveValue('Typing');
  });

  it('follows a poll when the field has no unsaved typing', () => {
    const { update } = setup();
    update({ link: { ...link, name: 'Elsewhere' } });
    expect(nameField()).toHaveValue('Elsewhere');
  });

  it('adopts values from elsewhere for later saves', async () => {
    const { update } = setup();
    update({ link: { ...link, url: 'http://elsewhere/' } });
    edit(nameField(), 'Gateway');
    await waitFor(() => expect(updateLink).toHaveBeenCalledWith(link.id, { ...details, name: 'Gateway', url: 'http://elsewhere/' }));
  });

  it('keeps typing a trailing space in a name', () => {
    setup();
    fireEvent.change(nameField(), { target: { value: 'Router ' } });
    expect(nameField()).toHaveValue('Router ');
  });
});

describe('LinkSettings deleting', () => {
  const openConfirm = () => fireEvent.click(screen.getByRole('button', { name: 'Delete link' }));

  it('asks first, and Cancel hides the question', () => {
    setup();
    openConfirm();
    expect(screen.getByRole('alertdialog')).toHaveTextContent("Delete Router? It's removed from the dashboard. Containers aren't affected.");
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(deleteLink).not.toHaveBeenCalled();
  });

  it('keeps keyboard focus through the question', async () => {
    setup();
    openConfirm();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus());
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Delete link' })).toHaveFocus());
  });

  it('deletes and reports back', async () => {
    const { onDeleted, onClose } = setup();
    openConfirm();
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(onDeleted).toHaveBeenCalled());
    expect(deleteLink).toHaveBeenCalledWith(link.id);
    expect(onClose).not.toHaveBeenCalled();
  });

  it('shows an alert and stays open when deleting fails', async () => {
    deleteLink.mockRejectedValue(new Error('down'));
    const { onDeleted } = setup();
    openConfirm();
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(await screen.findByRole('alert')).toHaveTextContent("Couldn't delete the link.");
    expect(onDeleted).not.toHaveBeenCalled();
    expect(screen.getByRole('heading', { name: 'Router' })).toBeInTheDocument();
  });
});

describe('LinkSettings icon', () => {
  const iconButton = () => screen.getByRole('button', { name: 'Change icon for Router' });

  it('saves a pick straight away, then returns with a notice and focus on the button', async () => {
    setup();
    fireEvent.click(iconButton());
    expect(screen.getByRole('searchbox', { name: 'Search icons' })).toHaveValue('Router');
    fireEvent.click(await screen.findByRole('button', { name: 'router' }));
    await waitFor(() => expect(selectLinkIcon).toHaveBeenCalledWith(link.id, 'router'));
    expect(await screen.findByText('Icon updated')).toBeInTheDocument();
    await waitFor(() => expect(iconButton()).toHaveFocus());
  });

  it('removes the notice after 4 seconds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      setup();
      fireEvent.click(iconButton());
      fireEvent.click(await screen.findByRole('button', { name: 'router' }));
      await screen.findByText('Icon updated');
      await act(async () => { vi.advanceTimersByTime(4000); });
      expect(screen.queryByText('Icon updated')).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it('keeps typed values across the picker', async () => {
    setup();
    fireEvent.change(nameField(), { target: { value: 'Typing' } });
    fireEvent.click(iconButton());
    fireEvent.click(screen.getByRole('button', { name: 'Back to link settings' }));
    expect(nameField()).toHaveValue('Typing');
    await act(async () => {});
  });
});
