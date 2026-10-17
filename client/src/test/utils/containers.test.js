import {describe,it,expect} from 'vitest';
import {groupByProject,groupBySource} from '../../utils/containers';
describe('container grouping', () => {
 it('preserves order and keeps project names and standalone containers distinct', () => {
  const a={id:'1',project:null,sourceId:'walker'},b={id:'2',project:'null',sourceId:'mike'},c={id:'3',project:'__proto__',sourceId:'mike'},d={id:'4',project:'null',sourceId:'walker'};
  const projects=groupByProject([a,b,c,d]);
  expect(projects.get(null)).toEqual([a]);expect(projects.get('null')).toEqual([b,d]);expect(projects.get('__proto__')).toEqual([c]);
  expect(groupBySource([a,b,c,d]).get('walker')).toEqual([a,d]);
 });
 it('handles empty inventories', () => {expect(groupBySource([]).size).toBe(0);expect(groupByProject([]).size).toBe(0);});
});
