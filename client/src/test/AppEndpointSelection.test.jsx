import { afterEach, describe, it, expect, vi } from 'vitest';
import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import App from '../App';
import { fetchDashboard, selectContainerEndpoint } from '../api';
import { createMockContainer, publishedEndpoint, renderWithQuery, toDashboard } from './testUtils';

vi.mock('../api', () => ({ fetchDashboard: vi.fn(), selectContainerEndpoint: vi.fn() }));

const source = { id: 'local', name: 'Local', status: 'UP', error: null };
const initial = toDashboard({ sources: [source], containers: [createMockContainer()] });
const refreshed = toDashboard({ sources: [source], containers: [createMockContainer({ endpoints: [publishedEndpoint(8080, false)], hasWebUi: false })] });

describe('App endpoint selection refresh', () => {
  afterEach(() => localStorage.clear());

  it('keeps the dialog and prior inventory after a saved selection fails to refresh, then recovers through GET alone', async () => {
    fetchDashboard.mockResolvedValueOnce(initial).mockRejectedValueOnce(new Error('Offline')).mockResolvedValue(refreshed);
    selectContainerEndpoint.mockResolvedValue(undefined);
    const { queryClient } = renderWithQuery(<App />);
    expect(await screen.findByRole('link', { name: /Test Container/ })).toHaveAttribute('href', 'http://localhost:8080/');
    fireEvent.click(screen.getByRole('button', { name: 'Test Container settings' }));
    fireEvent.click(screen.getByRole('radio', { name: 'No web UI' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Selection saved, but containers could not be refreshed. Reload to see the change.');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'http://localhost:8080/' })).toBeChecked();
    expect(document.querySelector('.tile-main')).toHaveAttribute('href', 'http://localhost:8080/');
    expect(screen.getByRole('button', { name: /^Local/ })).toBeInTheDocument();
    await act(async () => queryClient.refetchQueries({ queryKey: ['dashboard'] }));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    expect(screen.getByRole('radio', { name: 'No web UI' })).toBeChecked();
    expect(selectContainerEndpoint).toHaveBeenCalledTimes(1);
    expect(fetchDashboard).toHaveBeenCalledTimes(3);
    fireEvent.keyDown(document, { key: 'Escape' });
    // Containers without a web UI are hidden by default, so closing the dialog lets the filter apply.
    expect(screen.queryByRole('button', { name: 'Test Container settings' })).not.toBeInTheDocument();
  });

  it('turns the tile into a chip after No web UI is saved while containers without a web UI are shown', async () => {
    localStorage.setItem('porthole_showAll', 'true');
    fetchDashboard.mockReset();
    fetchDashboard.mockResolvedValueOnce(initial).mockResolvedValue(refreshed);
    selectContainerEndpoint.mockReset();
    selectContainerEndpoint.mockResolvedValue(undefined);
    renderWithQuery(<App />);
    await screen.findByRole('link', { name: /Test Container/ });
    fireEvent.click(screen.getByRole('button', { name: 'Test Container settings' }));
    fireEvent.click(screen.getByRole('radio', { name: 'No web UI' }));
    await waitFor(() => expect(screen.getByRole('radio', { name: 'No web UI' })).toBeChecked());
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(await screen.findByRole('button', { name: 'Test Container settings' })).toHaveClass('chip');
  });

  it('opens a saved custom URL from the tile immediately while the dialog stays open', async () => {
    const url = 'https://custom.example.com/';
    const custom = toDashboard({ sources: [source], containers: [createMockContainer({ endpoints: [publishedEndpoint(8080, false), { id: 'custom', source: 'custom', url, selected: true }] })] });
    fetchDashboard.mockReset();
    fetchDashboard.mockResolvedValueOnce(initial).mockResolvedValue(custom);
    selectContainerEndpoint.mockReset();
    selectContainerEndpoint.mockResolvedValue(undefined);
    renderWithQuery(<App />);
    await screen.findByRole('link', { name: /Test Container/ });
    fireEvent.click(screen.getByRole('button', { name: 'Test Container settings' }));
    fireEvent.click(screen.getByRole('button', { name: 'Add custom URL' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Custom URL' }), { target: { value: url } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(document.querySelector('.tile-main')).toHaveAttribute('href', url));
    expect(selectContainerEndpoint).toHaveBeenCalledWith({ entryId: 'container:local:test-container', selectedEndpoint: { id: 'custom', source: 'custom', url } });
    expect(await screen.findByRole('radio', { name: 'Custom URL' })).toBeChecked();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
