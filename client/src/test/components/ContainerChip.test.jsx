import { describe, it, expect, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import ContainerChip from '../../components/ContainerChip';
import { renderWithQuery, createMockContainer } from '../testUtils';

const headless = (overrides) => createMockContainer({ hasEndpoints: false, endpoints: [], ...overrides });

describe('ContainerChip', () => {
  beforeEach(() => localStorage.clear());

  it('renders a labelled button with a dot and the display name', () => {
    renderWithQuery(<ContainerChip container={headless()} />);
    const chip = screen.getByRole('button', { name: 'Test Container settings' });
    expect(chip).toHaveClass('chip');
    expect(chip.querySelector('.status-dot')).toHaveClass('dot-ok');
    expect(chip.querySelector('.status-dot')).toHaveAttribute('title', 'Running');
    expect(chip).toHaveTextContent('Test Container');
    expect(chip).not.toHaveTextContent('·');
  });

  it('adds the status label for non-ok statuses', () => {
    renderWithQuery(<ContainerChip container={headless({ status: 'Up 2 hours (unhealthy)' })} />);
    const chip = screen.getByRole('button', { name: 'Test Container settings' });
    expect(chip).toHaveTextContent('Test Container · Unhealthy');
    expect(chip.querySelector('.status-dot')).toHaveClass('dot-bad');
  });

  it('shows Offline when the host is down', () => {
    renderWithQuery(<ContainerChip container={headless()} sourceOnline={false} />);
    expect(screen.getByRole('button')).toHaveTextContent('Test Container · Offline');
  });

  it('puts the host tag after the name', () => {
    renderWithQuery(<ContainerChip container={headless()} hostTag="Walker" />);
    expect(screen.getByRole('button')).toHaveTextContent('Test Container · Walker');
  });

  it('opens the settings dialog on click', () => {
    renderWithQuery(<ContainerChip container={headless()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Test Container settings' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
