import { describe, it, expect } from 'vitest';
import { groupByProject } from '../../utils/containers';

describe('containers utils', () => {
  describe('groupByProject', () => {
    it('should group containers by project name', () => {
      const containers = [
        { id: '1', name: 'app1', project: 'project-a' },
        { id: '2', name: 'app2', project: 'project-a' },
        { id: '3', name: 'app3', project: 'project-b' },
      ];

      const result = groupByProject(containers);

      expect(result).toEqual({
        'project-a': [
          { id: '1', name: 'app1', project: 'project-a' },
          { id: '2', name: 'app2', project: 'project-a' },
        ],
        'project-b': [
          { id: '3', name: 'app3', project: 'project-b' },
        ],
      });
    });

    it('should group containers without project under null', () => {
      const containers = [
        { id: '1', name: 'standalone1' },
        { id: '2', name: 'standalone2', project: null },
        { id: '3', name: 'app1', project: 'project-a' },
      ];

      const result = groupByProject(containers);

      expect(result).toEqual({
        null: [
          { id: '1', name: 'standalone1' },
          { id: '2', name: 'standalone2', project: null },
        ],
        'project-a': [
          { id: '3', name: 'app1', project: 'project-a' },
        ],
      });
    });

    it('should return empty object for empty array', () => {
      const result = groupByProject([]);
      expect(result).toEqual({});
    });

    it('should handle all containers without project', () => {
      const containers = [
        { id: '1', name: 'standalone1' },
        { id: '2', name: 'standalone2' },
      ];

      const result = groupByProject(containers);

      expect(result).toEqual({
        null: [
          { id: '1', name: 'standalone1' },
          { id: '2', name: 'standalone2' },
        ],
      });
    });

    it('should preserve container order within groups', () => {
      const containers = [
        { id: '1', name: 'first', project: 'proj' },
        { id: '2', name: 'second', project: 'proj' },
        { id: '3', name: 'third', project: 'proj' },
      ];

      const result = groupByProject(containers);

      expect(result['proj'][0].name).toBe('first');
      expect(result['proj'][1].name).toBe('second');
      expect(result['proj'][2].name).toBe('third');
    });
  });

});
