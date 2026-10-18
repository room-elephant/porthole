import {describe,it,expect} from 'vitest';
import {groupByProject,groupBySource,buildSections} from '../../utils/containers';
describe('container grouping', () => {
 it('preserves order and keeps project names and standalone containers distinct', () => {
  const a={id:'1',project:null,sourceId:'walker'},b={id:'2',project:'null',sourceId:'mike'},c={id:'3',project:'__proto__',sourceId:'mike'},d={id:'4',project:'null',sourceId:'walker'};
  const projects=groupByProject([a,b,c,d]);
  expect(projects.get(null)).toEqual([a]);expect(projects.get('null')).toEqual([b,d]);expect(projects.get('__proto__')).toEqual([c]);
  expect(groupBySource([a,b,c,d]).get('walker')).toEqual([a,d]);
 });
 it('handles empty inventories', () => {expect(groupBySource([]).size).toBe(0);expect(groupByProject([]).size).toBe(0);});
});

describe('buildSections', () => {
 const walker={id:'walker',name:'Walker'},mike={id:'mike',name:'Mike'};
 const a={id:'a',name:'alpha',project:null,sourceId:'walker'},b={id:'b',name:'bravo',project:'smh',sourceId:'walker'},
  c={id:'c',name:'charlie',project:'base',sourceId:'walker'},d={id:'d',name:'delta',project:'base',sourceId:'mike'};
 it('groups host › stack with standalone first and stacks sorted', () => {
  const s=buildSections({sources:[walker,mike],containers:[b,a,c,d],groupBy:'host-stack'});
  expect(s.map(x=>x.title)).toEqual(['Walker','Mike']);
  expect(s[0].groups.map(g=>g.label)).toEqual([null,'base','smh']);
  expect(s[0].id).toBe('walker');expect(s[0].source).toBe(walker);expect(s[0].kind).toBe('host');
 });
 it('flattens host grouping', () => {
  const s=buildSections({sources:[walker],containers:[a,b,c],groupBy:'host'});
  expect(s[0].groups).toHaveLength(1);expect(s[0].groups[0].containers).toHaveLength(3);
 });
 it('groups by stack across hosts', () => {
  const s=buildSections({sources:[walker,mike],containers:[a,b,c,d],groupBy:'stack'});
  expect(s.map(x=>x.title)).toEqual(['Standalone','base','smh']);
  expect(s[1].hostNames).toEqual(['Walker','Mike']);expect(s[1].kind).toBe('stack');
  expect(s[1].id).toBe('stack:base');expect(s[1].source).toBeNull();expect(s[1].containers).toEqual([c,d]);
 });
 it('keeps empty host sections without a query but drops them with one', () => {
  expect(buildSections({sources:[walker,mike],containers:[a],groupBy:'host-stack'})).toHaveLength(2);
  expect(buildSections({sources:[walker,mike],containers:[a],groupBy:'host-stack',query:a.name}).map(x=>x.id)).toEqual(['walker']);
 });
 it('restricts to the selected host', () => {
  expect(buildSections({sources:[walker,mike],containers:[a,d],groupBy:'stack',hostFilter:'mike'}).flatMap(x=>x.containers)).toEqual([d]);
 });
 it('drops containers from unknown sources', () => {
  const orphan={id:'o',name:'orphan',project:null,sourceId:'ghost'};
  expect(buildSections({sources:[walker],containers:[a,orphan],groupBy:'host'})[0].containers).toEqual([a]);
 });
 it('keeps literal "null" and "__proto__" projects as named stacks', () => {
  const n={id:'n',name:'n',project:'null',sourceId:'walker'},p={id:'p',name:'p',project:'__proto__',sourceId:'walker'};
  const s=buildSections({sources:[walker],containers:[n,a,p],groupBy:'host-stack'});
  expect(s[0].groups.map(g=>g.label)).toEqual([null,'__proto__','null']);
  expect(s[0].groups[0].key).toBeNull();expect(s[0].groups[0].containers).toEqual([a]);
 });
 it('falls back to host-stack for an unknown groupBy', () => {
  const s=buildSections({sources:[walker],containers:[a,b],groupBy:'nope'});
  expect(s[0].groups.map(g=>g.label)).toEqual([null,'smh']);
 });
});
