import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import ContainerIcon from '../../components/ContainerIcon';

const container = { name: 'sonarr', iconUrl: 'https://example.test/sonarr.webp' };
const status = { kind: 'ok', dot: 'running', label: null };

describe('ContainerIcon', () => {
  it('uses the Porthole mark for the self container', () => {
    const { container: root } = render(<ContainerIcon container={{ ...container, self: true }} status={status} />);
    expect(root.querySelector('img')).toHaveAttribute('src', 'porthole-mark.svg');
  });

  it('uses the iconUrl for a non-self container named porthole', () => {
    const { container: root } = render(<ContainerIcon container={{ name: 'porthole', iconUrl: 'https://example.test/p.webp', self: false }} status={status} />);
    expect(root.querySelector('img')).toHaveAttribute('src', 'https://example.test/p.webp');
  });

  it('sizes through a custom property so stylesheet rules can override it', () => {
    const { container: root } = render(<ContainerIcon container={container} status={status} size={32} />);
    const icon = root.querySelector('.container-icon');
    expect(icon.style.getPropertyValue('--icon-size')).toBe('32px');
    expect(icon.style.width).toBe('');
    expect(icon.style.height).toBe('');
  });
});
