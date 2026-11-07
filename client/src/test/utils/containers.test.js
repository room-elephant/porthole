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

describe('buildSections with links', () => {
 const walker={id:'walker',name:'Walker'};
 const web={id:'w',name:'web',project:'base',sourceId:'walker'},solo={id:'s',name:'solo',project:null,sourceId:'walker'};
 const router={id:'1',name:'Router',url:'http://192.168.50.1/',stack:'base'},nas={id:'2',name:'NAS',url:'https://nas.lan/',stack:null},
  mail={id:'3',name:'Mail',url:'https://mail.lan/',stack:'zeta'},alpha={id:'4',name:'Alpha',url:'https://alpha.lan/',stack:null};
 const links=[router,nas,mail];
 const build=(extra={})=>buildSections({sources:[walker],containers:[web,solo],links,groupBy:'host-stack',...extra});
 const external=sections=>sections.find(section=>section.kind==='external');

 it('host › stack puts External after hosts, with no-stack links first', () => {
  const s=build();
  expect(s.map(x=>x.id)).toEqual(['walker','_external']);
  expect(external(s)).toMatchObject({title:'External',source:null,hostNames:['External'],containers:[]});
  expect(external(s).links.map(l=>l.name)).toEqual(['Mail','NAS','Router']);
  expect(external(s).groups.map(g=>g.label)).toEqual([null,'base','zeta']);
  expect(external(s).groups[0].links).toEqual([nas]);
  expect(external(s).groups[1].links).toEqual([router]);
 });
 it('host grouping has one External group with all links', () => {
  const s=external(build({groupBy:'host'}));
  expect(s.groups).toHaveLength(1);
  expect(s.groups[0].links.map(l=>l.name)).toEqual(['Mail','NAS','Router']);
 });
 it('host sections have no links', () => {
  expect(build()[0].links).toEqual([]);
  expect(build()[0].groups.every(g=>g.links.length===0)).toBe(true);
 });
 it('stack grouping mixes links with containers of the same stack', () => {
  const s=build({groupBy:'stack'});
  expect(s.map(x=>x.title)).toEqual(['Standalone','base','zeta']);
  expect(s[1].containers).toEqual([web]);expect(s[1].links).toEqual([router]);
  expect(s[1].groups[0].links).toEqual([router]);
  expect(s[1].hostNames).toEqual(['Walker','External']);
 });
 it('stack grouping gives a link-only stack its own section and no-stack links join Standalone', () => {
  const s=build({groupBy:'stack'});
  expect(s[2]).toMatchObject({title:'zeta',containers:[],links:[mail],hostNames:['External']});
  expect(s[0].links).toEqual([nas]);expect(s[0].containers).toEqual([solo]);
 });
 it('stack sections without links keep their hosts', () => {
  const s=buildSections({sources:[walker],containers:[web],links:[],groupBy:'stack'});
  expect(s[0].hostNames).toEqual(['Walker']);expect(s[0].links).toEqual([]);
 });
 it('matches stack names exactly', () => {
  const s=buildSections({sources:[walker],containers:[web],links:[{...router,stack:'Base'}],groupBy:'stack'});
  expect(s.map(x=>x.title)).toHaveLength(2);expect(s.map(x=>x.title)).toEqual(expect.arrayContaining(['Base','base']));
 });
 it('has no External section without links in host modes', () => {
  for(const groupBy of ['host','host-stack']){
   expect(external(buildSections({sources:[walker],containers:[web],links:[],groupBy}))).toBeUndefined();
   expect(external(buildSections({sources:[walker],containers:[web],groupBy}))).toBeUndefined();
  }
 });
 it('selecting External shows only links in every mode', () => {
  for(const groupBy of ['host','host-stack','stack']){
   const s=build({groupBy,hostFilter:'_external'});
   expect(s.flatMap(x=>x.containers)).toEqual([]);
   expect(s.flatMap(x=>x.links)).toHaveLength(3);
  }
 });
 it('selecting a host hides links', () => {
  for(const groupBy of ['host','host-stack','stack']){
   expect(build({groupBy,hostFilter:'walker'}).flatMap(x=>x.links)).toEqual([]);
  }
 });
 it('filters links by name, URL and External, dropping the section when none match', () => {
  expect(external(build({query:'rout'})).links).toEqual([router]);
  expect(external(build({query:'192.168'})).links).toEqual([router]);
  expect(external(build({query:'external'})).links).toHaveLength(3);
  expect(external(build({query:'web'}))).toBeUndefined();
 });
 it('sorts links by name inside a group', () => {
  const s=buildSections({sources:[],containers:[],links:[nas,alpha],groupBy:'host'});
  expect(s[0].groups[0].links.map(l=>l.name)).toEqual(['Alpha','NAS']);
 });
 it('shows links with no Docker sources at all', () => {
  expect(buildSections({sources:[],containers:[],links,groupBy:'stack'}).flatMap(x=>x.links)).toHaveLength(3);
 });
});
