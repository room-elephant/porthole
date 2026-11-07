import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import IconPicker from '../../components/IconPicker';
import { fetchIconLibrary } from '../../api';
import { imageSearchTerm } from '../../utils/icons';
import { createMockContainer, renderWithQuery } from '../testUtils';

vi.mock('../../api', () => ({ fetchIconLibrary: vi.fn() }));

const CDN = 'https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp';
const metadata = {
  coredns: {},
  grafana: { aliases: ['dashboards'] },
  transmission: {},
  'transmission-alt': {},
};
const manyIcons = Object.fromEntries(Array.from({ length: 250 }, (_, i) => [`icon-${String(i).padStart(3, '0')}`, {}]));
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
const container = (overrides = {}) => createMockContainer({
  name: 'app',
  image: 'lscr.io/linuxserver/transmission:latest',
  iconUrl: `${CDN}/transmission.webp`,
  ...overrides,
});
const source = ({ iconUrl, image }) => ({ iconUrl, startingSearch: imageSearchTerm(image) });
const renderPicker = (props = {}) => {
  const icon = props.icon ?? iconActions();
  const onSaved = vi.fn();
  renderWithQuery(<IconPicker {...source(props.container ?? container())} icon={icon} onSaved={onSaved} />);
  return { icon, onSaved };
};
const tile = name => screen.findByRole('button', { name: new RegExp(`^${name}(, current icon)?$`) });
const search = () => screen.getByRole('searchbox', { name: 'Search icons' });
const pngFile = new File(['x'], 'logo.png', { type: 'image/png' });

beforeEach(() => {
  vi.clearAllMocks();
  fetchIconLibrary.mockResolvedValue(metadata);
});

describe('IconPicker starting tab', () => {
  it('opens on Library for an automatic or library icon', async () => {
    renderPicker();
    expect(screen.getByRole('tab', { name: 'Library' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: 'Upload' })).toHaveAttribute('aria-selected', 'false');
    await tile('transmission');
  });

  it('opens on Upload for an uploaded icon, with a preview', () => {
    renderPicker({ container: container({ iconUrl: '/api/icons/abc.webp' }) });
    expect(screen.getByRole('tab', { name: 'Upload' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('heading', { name: 'Your uploaded icon' })).toBeInTheDocument();
    expect(document.querySelector('.icon-upload-preview img')).toHaveAttribute('src', '/api/icons/abc.webp');
    expect(screen.getByText('Current')).toBeInTheDocument();
  });

  it('clears a stale failed-save alert when opening', () => {
    const { icon } = renderPicker();
    expect(icon.clearError).toHaveBeenCalledTimes(1);
  });
});

describe('IconPicker library tab', () => {
  it('starts the search from the image name and filters to it', async () => {
    renderPicker();
    expect(search()).toHaveValue('transmission');
    expect(await screen.findByRole('status')).toHaveTextContent('2 matches of 4');
    expect(screen.queryByRole('button', { name: 'coredns' })).not.toBeInTheDocument();
  });

  it('keeps the count as a heading while announcing it', async () => {
    renderPicker();
    expect(await screen.findByRole('heading', { name: '2 matches of 4' })).toBeInTheDocument();
    expect(screen.getByRole('status').closest('h3')).not.toBeNull();
  });

  it('announces only the message for no matches, not its actions', async () => {
    renderPicker({ container: container({ image: 'my-custom-app:1' }) });
    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('No icons match “my-custom-app”.');
    expect(within(status).queryByRole('button')).not.toBeInTheDocument();
  });

  it('uses the singular for one match', async () => {
    renderPicker({ container: container({ image: 'grafana/grafana:11' }) });
    expect(await screen.findByRole('status')).toHaveTextContent('1 match of 4');
  });

  it('searches aliases and ignores punctuation', async () => {
    renderPicker();
    await tile('transmission');
    fireEvent.change(search(), { target: { value: 'dashboards' } });
    expect(await tile('grafana')).toBeInTheDocument();
    fireEvent.change(search(), { target: { value: 'transmission_alt' } });
    expect(await tile('transmission-alt')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^grafana/ })).not.toBeInTheDocument();
  });

  it('marks only the current tile', async () => {
    renderPicker();
    const current = await screen.findByRole('button', { name: 'transmission, current icon' });
    expect(current).toHaveAttribute('aria-current', 'true');
    expect(within(current).getByText('Current')).toBeInTheDocument();
    const other = await tile('transmission-alt');
    expect(other).not.toHaveAttribute('aria-current');
    expect(within(other).queryByText('Current')).not.toBeInTheDocument();
  });

  it('marks nothing when the icon in use is not in the library', async () => {
    renderPicker({ container: container({ iconUrl: `${CDN}/my-custom-app.webp` }) });
    await tile('transmission');
    expect(document.querySelector('[aria-current]')).toBeNull();
  });

  it('renders the real icon with the name as its tooltip', async () => {
    renderPicker();
    const button = await tile('transmission-alt');
    expect(button).toHaveAttribute('title', 'transmission-alt');
    expect(button.querySelector('img')).toHaveAttribute('src', `${CDN}/transmission-alt.webp`);
    expect(button.querySelector('img')).toHaveAttribute('loading', 'lazy');
  });

  it('offers a clear control only when the search has text, and it restores all icons', async () => {
    renderPicker();
    await tile('transmission');
    fireEvent.click(screen.getByRole('button', { name: 'Clear search' }));
    expect(search()).toHaveValue('');
    expect(await tile('coredns')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('4 icons');
    expect(screen.queryByRole('button', { name: 'Clear search' })).not.toBeInTheDocument();
  });

  it('starts with an empty search for an unusable image name', async () => {
    renderPicker({ container: container({ image: 'sha256:abc' }) });
    expect(search()).toHaveValue('');
    expect(await tile('coredns')).toBeInTheDocument();
  });

  it('renders icons in pages and adds more while scrolling', async () => {
    fetchIconLibrary.mockResolvedValue(manyIcons);
    renderPicker({ container: container({ image: 'sha256:abc' }) });
    await tile('icon-099');
    expect(screen.queryByRole('button', { name: 'icon-100' })).not.toBeInTheDocument();
    const grid = document.querySelector('.icon-grid');
    Object.defineProperties(grid, {
      scrollHeight: { configurable: true, value: 1000 },
      clientHeight: { configurable: true, value: 300 },
      scrollTop: { configurable: true, value: 700 },
    });
    fireEvent.scroll(grid);
    expect(await tile('icon-199')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'icon-200' })).not.toBeInTheDocument();
  });

  it('does not add more while far from the end', async () => {
    fetchIconLibrary.mockResolvedValue(manyIcons);
    renderPicker({ container: container({ image: 'sha256:abc' }) });
    await tile('icon-099');
    const grid = document.querySelector('.icon-grid');
    Object.defineProperties(grid, {
      scrollHeight: { configurable: true, value: 1000 },
      clientHeight: { configurable: true, value: 300 },
      scrollTop: { configurable: true, value: 0 },
    });
    fireEvent.scroll(grid);
    expect(screen.queryByRole('button', { name: 'icon-100' })).not.toBeInTheDocument();
  });

  it('shows no-match actions, and Upload your own switches tab', async () => {
    renderPicker({ container: container({ image: 'my-custom-app:1' }) });
    expect(await screen.findByText('No icons match “my-custom-app”.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Upload your own' }));
    expect(screen.getByRole('tab', { name: 'Upload' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText('Drop an image here, or browse')).toBeInTheDocument();
  });

  it('Clear search recovers from no matches', async () => {
    renderPicker({ container: container({ image: 'my-custom-app:1' }) });
    await screen.findByText('No icons match “my-custom-app”.');
    const panel = document.querySelector('.icon-empty');
    fireEvent.click(within(panel).getByRole('button', { name: 'Clear search' }));
    expect(await tile('coredns')).toBeInTheDocument();
  });

  it('reports an unavailable library, and Retry loads it', async () => {
    fetchIconLibrary.mockRejectedValueOnce(new Error('offline'));
    renderPicker();
    expect(await screen.findByText("Couldn't load the icon library.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await tile('transmission')).toBeInTheDocument();
  });

  it('Upload your own works while the library is unavailable', async () => {
    fetchIconLibrary.mockRejectedValue(new Error('offline'));
    renderPicker();
    await screen.findByText("Couldn't load the icon library.");
    fireEvent.click(screen.getByRole('button', { name: 'Upload your own' }));
    expect(screen.getByRole('tab', { name: 'Upload' })).toHaveAttribute('aria-selected', 'true');
  });

  it('shows only the Dashboard Icons credit in the footer', async () => {
    renderPicker();
    await tile('transmission');
    expect(screen.getByRole('link', { name: 'Dashboard Icons' })).toHaveAttribute('href', 'https://dashboardicons.com');
    expect(screen.queryByText(/reset/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/using the automatic/i)).not.toBeInTheDocument();
  });
});

describe('IconPicker saving', () => {
  it('saves a clicked icon straight away', async () => {
    const { icon, onSaved } = renderPicker();
    fireEvent.click(await tile('transmission-alt'));
    expect(icon.selectIcon).toHaveBeenCalledWith('transmission-alt', { onSaved });
  });

  it('marks the picked tile busy and disables everything else while saving', async () => {
    const icon = iconActions();
    const { rerender } = renderWithQuery(<IconPicker {...source(container())} icon={icon} onSaved={vi.fn()} />);
    fireEvent.click(await tile('transmission-alt'));
    rerender(<IconPicker {...source(container())} icon={{ ...icon, isSaving: true }} onSaved={vi.fn()} />);
    expect(await tile('transmission-alt')).toHaveAttribute('aria-busy', 'true');
    expect(await tile('transmission')).toBeDisabled();
    expect(screen.getByRole('tab', { name: 'Upload' })).toBeDisabled();
  });

  it('reports a failed save, and Try again retries', async () => {
    const { icon } = renderPicker({ icon: iconActions({ saveError: new Error('disk full') }) });
    await tile('transmission');
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent("Couldn't save the icon.");
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }));
    expect(icon.retry).toHaveBeenCalledTimes(1);
  });
});

describe('IconPicker server rejections', () => {
  const rejected = status => iconActions({ saveError: Object.assign(new Error('rejected'), { response: { status } }) });

  it.each([
    [400, "That file isn't a PNG, WebP, JPEG or SVG image."],
    [413, 'That file is over 1 MB. Choose an image up to 1 MB.'],
    [507, 'Porthole has reached its limit of 500 uploaded icons.'],
  ])('explains a %i without offering a pointless retry', async (status, message) => {
    renderPicker({ icon: rejected(status) });
    await tile('transmission');
    expect(screen.getByRole('alert')).toHaveTextContent(message);
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  });

  it('still offers a retry for a server failure', async () => {
    renderPicker({ icon: rejected(500) });
    await tile('transmission');
    expect(screen.getByRole('alert')).toHaveTextContent("Couldn't save the icon.");
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});

describe('IconPicker file drops', () => {
  it('stops a dropped file navigating away from the app', () => {
    renderPicker();
    expect(fireEvent.dragOver(document.body)).toBe(false);
    expect(fireEvent.drop(document.body)).toBe(false);
  });

  it('leaves drops on the file input alone', () => {
    renderPicker();
    fireEvent.click(screen.getByRole('tab', { name: 'Upload' }));
    expect(fireEvent.drop(screen.getByLabelText(/or browse/))).toBe(true);
  });

  it('stops intercepting drops once closed', () => {
    const { unmount } = renderWithQuery(<IconPicker {...source(container())} icon={iconActions()} onSaved={vi.fn()} />);
    unmount();
    expect(fireEvent.drop(document.body)).toBe(true);
  });
});

describe('IconPicker upload tab', () => {
  const openUpload = (props) => {
    const result = renderPicker(props);
    fireEvent.click(screen.getByRole('tab', { name: 'Upload' }));
    return result;
  };
  const choose = file => fireEvent.change(screen.getByLabelText(/or browse/), { target: { files: [file] } });

  it('asks for an image when the current icon is not an upload', () => {
    openUpload();
    expect(screen.getByText('Drop an image here, or browse')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Your uploaded icon' })).not.toBeInTheDocument();
    expect(screen.getByLabelText(/or browse/)).toHaveAttribute('accept', 'image/png,image/webp,image/jpeg,image/svg+xml');
  });

  it('asks to replace when the current icon is an upload', () => {
    renderPicker({ container: container({ iconUrl: '/api/icons/abc.webp' }) });
    expect(screen.getByText('Replace: drop a new image, or browse')).toBeInTheDocument();
  });

  it('rejects an unsupported file without uploading', () => {
    const { icon } = openUpload();
    choose(new File(['GIF89a'], 'logo.gif', { type: 'image/gif' }));
    expect(screen.getByRole('alert')).toHaveTextContent("logo.gif isn't supported. Use PNG, WebP, JPEG or SVG.");
    expect(icon.uploadIcon).not.toHaveBeenCalled();
  });

  it('uploads a valid file straight away and clears an earlier rejection', () => {
    const { icon, onSaved } = openUpload();
    choose(new File(['GIF89a'], 'logo.gif', { type: 'image/gif' }));
    choose(pngFile);
    expect(icon.uploadIcon).toHaveBeenCalledWith(pngFile, { onSaved });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows the file name while uploading', async () => {
    const icon = iconActions();
    const { rerender } = renderWithQuery(<IconPicker {...source(container())} icon={icon} onSaved={vi.fn()} />);
    fireEvent.click(screen.getByRole('tab', { name: 'Upload' }));
    choose(pngFile);
    rerender(<IconPicker {...source(container())} icon={{ ...icon, isSaving: true }} onSaved={vi.fn()} />);
    expect(screen.getByText('Uploading logo.png…')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Library' })).toBeDisabled();
    await act(async () => {});
  });

  it('keeps the search text when switching tabs and back', async () => {
    renderPicker();
    await tile('transmission');
    fireEvent.change(search(), { target: { value: 'gra' } });
    fireEvent.click(screen.getByRole('tab', { name: 'Upload' }));
    fireEvent.click(screen.getByRole('tab', { name: 'Library' }));
    await waitFor(() => expect(search()).toHaveValue('gra'));
  });
});

describe('IconPicker without a container', () => {
  it('starts from the given search and marks the given icon', async () => {
    renderWithQuery(<IconPicker iconUrl={`${CDN}/grafana.webp`} startingSearch="graf" icon={iconActions()} onSaved={vi.fn()} />);
    expect(search()).toHaveValue('graf');
    expect(await tile('grafana')).toHaveAttribute('aria-current', 'true');
  });

  it('copes with no icon yet', async () => {
    renderWithQuery(<IconPicker iconUrl={null} startingSearch="" icon={iconActions()} onSaved={vi.fn()} />);
    expect(await tile('coredns')).not.toHaveAttribute('aria-current');
  });
});

