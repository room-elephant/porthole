import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import ContainerSettings from '../../components/ContainerSettings';
import ContainerTile from '../../components/ContainerTile';
import { fetchIconLibrary, selectContainerIcon } from '../../api';
import { createMockContainer, publishedEndpoint, renderInventory, renderWithQuery } from '../testUtils';

vi.mock('../../api', () => ({
  fetchIconLibrary: vi.fn(),
  selectContainerEndpoint: vi.fn(),
  selectContainerIcon: vi.fn(),
  uploadContainerIcon: vi.fn(),
}));

const CDN = 'https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp';
const container = createMockContainer({
  name: 'transmission-1',
  displayName: 'transmission',
  image: 'lscr.io/linuxserver/transmission:latest',
  iconUrl: `${CDN}/transmission.webp`,
  endpoints: [publishedEndpoint(80)],
});
const iconActions = (overrides = {}) => ({
  selectIcon: vi.fn(),
  uploadIcon: vi.fn(),
  retry: vi.fn(),
  clearError: vi.fn(),
  isSaving: false,
  saveError: null,
  refreshError: null,
  ...overrides,
});
const props = (overrides = {}) => ({
  container,
  sourceName: 'Walker',
  status: { kind: 'update', dot: 'ok', label: '' },
  showUpdate: true,
  selectedEndpointId: 'published-port:80',
  checkUpdates: true,
  onClose: vi.fn(),
  onSelectEndpoint: vi.fn(),
  onToggleCheckUpdates: vi.fn(),
  icon: iconActions(),
  ...overrides,
});
const pencil = () => screen.getByRole('button', { name: 'Change icon for transmission' });
const openPicker = () => fireEvent.click(pencil());
const back = () => screen.getByRole('button', { name: 'Back to settings' });
const search = () => screen.getByRole('searchbox', { name: 'Search icons' });
const setPointer = (coarse) => {
  window.matchMedia = vi.fn().mockReturnValue({ matches: coarse });
};

beforeEach(() => {
  vi.clearAllMocks();
  fetchIconLibrary.mockResolvedValue({ transmission: {}, 'transmission-alt': {}, coredns: {} });
  delete window.matchMedia;
});

describe('ContainerSettings icon button', () => {
  it('offers the change on the header icon, keeping its badges', () => {
    renderWithQuery(<ContainerSettings {...props()} />);
    expect(pencil()).toHaveAttribute('title', 'Change icon');
    expect(pencil().querySelector('.icon-edit-pencil')).toBeInTheDocument();
    expect(pencil().querySelector('.status-dot')).toBeInTheDocument();
    expect(pencil().querySelector('.update-badge')).toBeInTheDocument();
    expect(pencil().querySelector('img')).toHaveAttribute('src', `${CDN}/transmission.webp`);
  });
});

describe('ContainerSettings view swap', () => {
  it('shows the picker in the same modal, and Back returns to settings', async () => {
    renderWithQuery(<ContainerSettings {...props()} />);
    openPicker();
    expect(screen.getByRole('dialog', { name: 'Choose icon' })).toBeInTheDocument();
    expect(screen.queryByText('Tile opens')).not.toBeInTheDocument();
    await screen.findByRole('button', { name: 'transmission, current icon' });
    fireEvent.click(back());
    expect(screen.getByRole('dialog', { name: 'transmission' })).toBeInTheDocument();
    expect(screen.getByText('Tile opens')).toBeInTheDocument();
  });

  it('Close and Esc close the modal from the picker', () => {
    const onClose = vi.fn();
    renderWithQuery(<ContainerSettings {...props({ onClose })} />);
    openPicker();
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(onClose).toHaveBeenCalledTimes(1);
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it('disables Back during a save but not Close', () => {
    const { rerender } = renderWithQuery(<ContainerSettings {...props()} />);
    openPicker();
    rerender(<ContainerSettings {...props({ icon: iconActions({ isSaving: true }) })} />);
    expect(back()).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Close' })).toBeEnabled();
  });

  it('keeps the view, tab and typed search through an inventory poll', async () => {
    const shared = props();
    const { rerender } = renderWithQuery(<ContainerSettings {...shared} />);
    openPicker();
    await screen.findByRole('button', { name: 'transmission, current icon' });
    fireEvent.change(search(), { target: { value: 'core' } });
    rerender(<ContainerSettings {...shared} container={{ ...container, status: 'Up 3 hours' }} status={{ kind: 'ok', dot: 'ok', label: '' }} />);
    expect(screen.getByRole('dialog', { name: 'Choose icon' })).toBeInTheDocument();
    expect(search()).toHaveValue('core');
    expect(screen.getByRole('button', { name: 'coredns' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: 'Upload' }));
    rerender(<ContainerSettings {...shared} container={{ ...container, status: 'Up 4 hours' }} />);
    expect(screen.getByRole('tab', { name: 'Upload' })).toHaveAttribute('aria-selected', 'true');
  });
});

describe('ContainerSettings after saving', () => {
  afterEach(() => { vi.useRealTimers(); });

  const saveFromPicker = async (icon) => {
    openPicker();
    fireEvent.change(search(), { target: { value: 'core' } });
    fireEvent.click(await screen.findByRole('button', { name: 'coredns' }));
    await act(async () => { icon.selectIcon.mock.calls[0][1].onSaved(); });
  };

  it('returns to settings and announces the update', async () => {
    const icon = iconActions();
    renderWithQuery(<ContainerSettings {...props({ icon })} />);
    await saveFromPicker(icon);
    expect(screen.getByText('Tile opens')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Icon updated');
    expect(screen.queryByText(/transmission-1 · Walker/)).not.toBeInTheDocument();
  });

  it('restores the subtitle after 4 seconds', async () => {
    const icon = iconActions();
    renderWithQuery(<ContainerSettings {...props({ icon })} />);
    openPicker();
    fireEvent.change(search(), { target: { value: 'core' } });
    fireEvent.click(await screen.findByRole('button', { name: 'coredns' }));
    vi.useFakeTimers();
    await act(async () => { icon.selectIcon.mock.calls[0][1].onSaved(); });
    expect(screen.getByText('Icon updated')).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(3999); });
    expect(screen.getByText('Icon updated')).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(1); });
    expect(screen.queryByText('Icon updated')).not.toBeInTheDocument();
    expect(screen.getByText(/transmission-1 · Walker/)).toBeInTheDocument();
  });

  it('explains a saved icon whose refresh failed', async () => {
    const icon = iconActions();
    const { rerender } = renderWithQuery(<ContainerSettings {...props({ icon })} />);
    await saveFromPicker(icon);
    rerender(<ContainerSettings {...props({ icon: { ...icon, refreshError: new Error('offline') } })} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Icon saved, but containers could not be refreshed. Reload to see the change.');
    expect(screen.queryByText('Icon updated')).not.toBeInTheDocument();
  });

  it('keeps explaining the failed refresh after the update notice would have ended', async () => {
    const icon = iconActions();
    const { rerender } = renderWithQuery(<ContainerSettings {...props({ icon })} />);
    openPicker();
    fireEvent.change(search(), { target: { value: 'core' } });
    fireEvent.click(await screen.findByRole('button', { name: 'coredns' }));
    vi.useFakeTimers();
    await act(async () => { icon.selectIcon.mock.calls[0][1].onSaved(); });
    rerender(<ContainerSettings {...props({ icon: { ...icon, refreshError: new Error('offline') } })} />);
    await act(async () => { vi.advanceTimersByTime(5000); });
    expect(screen.getByRole('alert')).toHaveTextContent('Icon saved, but containers could not be refreshed.');
  });

  it('explains a failed refresh when reopened after closing during the save', () => {
    renderWithQuery(<ContainerSettings {...props({ icon: iconActions({ refreshError: new Error('offline') }) })} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Icon saved, but containers could not be refreshed.');
  });
});

describe('ContainerSettings focus', () => {
  it('focuses and selects the search when the picker opens', () => {
    renderWithQuery(<ContainerSettings {...props()} />);
    openPicker();
    expect(search()).toHaveFocus();
    expect(search().selectionStart).toBe(0);
    expect(search().selectionEnd).toBe('transmission'.length);
  });

  it('does not focus the search on a touch device', () => {
    setPointer(true);
    renderWithQuery(<ContainerSettings {...props()} />);
    openPicker();
    expect(search()).not.toHaveFocus();
    expect(document.querySelector('.icon-picker')).toHaveFocus();
  });

  it('focuses the search on a mouse device', () => {
    setPointer(false);
    renderWithQuery(<ContainerSettings {...props()} />);
    openPicker();
    expect(search()).toHaveFocus();
  });

  it('Back returns focus to the icon button', () => {
    renderWithQuery(<ContainerSettings {...props()} />);
    openPicker();
    fireEvent.click(back());
    expect(pencil()).toHaveFocus();
  });

  it('a successful save returns focus to the icon button', async () => {
    const icon = iconActions();
    renderWithQuery(<ContainerSettings {...props({ icon })} />);
    openPicker();
    fireEvent.change(search(), { target: { value: 'core' } });
    fireEvent.click(await screen.findByRole('button', { name: 'coredns' }));
    await act(async () => { icon.selectIcon.mock.calls[0][1].onSaved(); });
    expect(pencil()).toHaveFocus();
  });
});

describe('Changing an icon from a tile', () => {
  const deferred = () => { let resolve; const promise = new Promise(yes => { resolve = yes; }); return { promise, resolve }; };
  const inventory = { sources: [], containers: [container] };
  const renderTile = (fetch) => renderInventory(data => <ContainerTile container={data.containers[0]} />, inventory, fetch);
  const openSettings = () => fireEvent.click(screen.getAllByRole('button', { name: /settings/i })[0]);

  it('has no pencil on the tile itself', () => {
    renderTile(vi.fn().mockResolvedValue(inventory));
    expect(document.querySelector('.icon-edit-pencil')).toBeNull();
  });

  it('keeps saving after the modal closes, and the tile shows the new icon', async () => {
    const write = deferred();
    selectContainerIcon.mockReturnValue(write.promise);
    const updated = { ...inventory, containers: [{ ...container, iconUrl: `${CDN}/coredns.webp` }] };
    const fetch = vi.fn().mockResolvedValue(updated);
    renderTile(fetch);
    openSettings();
    openPicker();
    fireEvent.change(search(), { target: { value: 'core' } });
    fireEvent.click(await screen.findByRole('button', { name: 'coredns' }));
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    write.resolve();
    await waitFor(() => expect(fetch).toHaveBeenCalled());
    await waitFor(() => expect(document.querySelector('.container-icon img')).toHaveAttribute('src', `${CDN}/coredns.webp`));
    expect(selectContainerIcon).toHaveBeenCalledWith({ sourceId: 'local', containerName: 'transmission-1', name: 'coredns' });
  });
});
