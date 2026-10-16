import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { publishedEndpoint } from '../testUtils';
import ContainerSettings from '../../components/ContainerSettings';

describe('ContainerSettings', () => {
  const defaultProps = {
    containerName: 'nginx',
    endpoints: [publishedEndpoint(80), publishedEndpoint(443)],
    selectedEndpointId: 'published-port:80',
    checkUpdates: true,
    onClose: vi.fn(),
    onSelectEndpoint: vi.fn(),
    onToggleCheckUpdates: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render container name as title', () => {
    render(<ContainerSettings {...defaultProps} />);
    expect(screen.getByText('nginx')).toBeInTheDocument();
  });

  it('should render port options', () => {
    render(<ContainerSettings {...defaultProps} />);

    expect(screen.getByText('http://localhost:80/')).toBeInTheDocument();
    expect(screen.getByText('http://localhost:443/')).toBeInTheDocument();
  });

  it('should show checkmark for selected port', () => {
    render(<ContainerSettings {...defaultProps} selectedEndpointId="published-port:80" />);

    const selectedEndpointOption = screen.getByText('http://localhost:80/').closest('.endpoint-option');
    expect(selectedEndpointOption).toHaveClass('selected');
    expect(selectedEndpointOption).toHaveTextContent('✓');
  });

  it('should call onSelectEndpoint when port is clicked', () => {
    const onSelectEndpoint = vi.fn();
    render(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} />);

    fireEvent.click(screen.getByText('http://localhost:443/'));

    expect(onSelectEndpoint).toHaveBeenCalledWith('published-port:443');
  });

  it('offers no endpoint in the same selection group as all URL sources', async () => {
    const onSelectEndpoint = vi.fn();
    const user = userEvent.setup();
    render(<ContainerSettings {...defaultProps} selectedEndpointId="none" onSelectEndpoint={onSelectEndpoint} endpoints={[
      { id: 'root', source: 'traefik', url: 'https://app.example.com/' },
      publishedEndpoint(80),
    ]} />);

    const group = screen.getByRole('group', { name: 'Endpoint' });
    const radios = within(group).getAllByRole('radio');
    expect(radios).toHaveLength(3);
    expect(new Set(radios.map(radio => radio.name)).size).toBe(1);
    expect(within(group).getByRole('radio', { name: 'No endpoint' })).toBeChecked();
    expect(screen.getAllByText('✓')).toHaveLength(1);

    await user.click(within(group).getByRole('radio', { name: 'https://app.example.com/' }));
    expect(onSelectEndpoint).toHaveBeenCalledWith('root');
  });

  it('allows selecting no endpoint from the keyboard', async () => {
    const onSelectEndpoint = vi.fn();
    const user = userEvent.setup();
    render(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} />);
    screen.getByRole('radio', { name: 'No endpoint' }).focus();
    await user.keyboard(' ');
    expect(onSelectEndpoint).toHaveBeenCalledWith('none');
  });

  it('should render version updates toggle', () => {
    render(<ContainerSettings {...defaultProps} />);

    expect(screen.getByText('Show update notifications')).toBeInTheDocument();
    expect(screen.getByRole('checkbox')).toBeInTheDocument();
  });

  it('should have toggle checked when checkUpdates is true', () => {
    render(<ContainerSettings {...defaultProps} checkUpdates={true} />);

    const checkbox = screen.getByRole('checkbox');
    expect(checkbox).toBeChecked();
  });

  it('should have toggle unchecked when checkUpdates is false', () => {
    render(<ContainerSettings {...defaultProps} checkUpdates={false} />);

    const checkbox = screen.getByRole('checkbox');
    expect(checkbox).not.toBeChecked();
  });

  it('should call onToggleCheckUpdates when toggle is changed', () => {
    const onToggleCheckUpdates = vi.fn();
    render(
      <ContainerSettings
        {...defaultProps}
        checkUpdates={false}
        onToggleCheckUpdates={onToggleCheckUpdates}
      />
    );

    fireEvent.click(screen.getByRole('checkbox'));

    expect(onToggleCheckUpdates).toHaveBeenCalledWith(true);
  });

  it('should call onClose when modal is closed', () => {
    const onClose = vi.fn();
    render(<ContainerSettings {...defaultProps} onClose={onClose} />);

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('should not render port section when no ports', () => {
    render(<ContainerSettings {...defaultProps} endpoints={[]} />);

    expect(screen.queryByText('Published Ports')).not.toBeInTheDocument();
    expect(screen.getByText('Version Updates')).toBeInTheDocument();
  });

  it('should render description text', () => {
    render(<ContainerSettings {...defaultProps} />);

    expect(
      screen.getByText(/Version checks run in the background/)
    ).toBeInTheDocument();
  });
  it('groups all sources and offers one accessible selection across sections', async () => {
    const onSelectEndpoint = vi.fn();
    const user = userEvent.setup();
    render(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} endpoints={[
      { id: 'root', source: 'traefik', url: 'https://app.example.com/' },
      { id: 'dl', source: 'traefik', url: 'https://app.example.com/dl/' },
      publishedEndpoint(80),
      { id: 'custom', source: 'ngnext', url: 'https://custom.example.com/' },
      { id: 'proto', source: '__proto__', url: 'https://proto.example.com/' },
    ]} />);
    expect(screen.getAllByRole('heading', { level: 5 }).map(heading => heading.textContent)).toEqual(['Traefik URLs', 'Published Ports', 'Proto URLs', 'Ngnext URLs']);
    expect(screen.getByRole('group', { name: 'Endpoint' })).toBeInTheDocument();
    expect(screen.getAllByRole('radio').filter(radio => radio.checked)).toHaveLength(1);
    expect(screen.getAllByText('✓')).toHaveLength(1);
    screen.getByRole('radio', { name: 'https://proto.example.com/' }).focus();
    await user.keyboard(' ');
    expect(onSelectEndpoint).toHaveBeenCalledWith('proto');
    await user.click(screen.getByRole('radio', { name: 'https://custom.example.com/' }));
    expect(onSelectEndpoint).toHaveBeenCalledWith('custom');
  });

  it('shows no checked option when selection is ambiguous', () => {
    render(<ContainerSettings {...defaultProps} selectedEndpointId={null} />);
    expect(screen.getAllByRole('radio').filter(radio => radio.checked)).toHaveLength(0);
    expect(screen.queryByText('✓')).not.toBeInTheDocument();
  });

});
