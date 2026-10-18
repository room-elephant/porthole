import {GROUP_BY} from '../constants';
import {matchesFilter} from './filter';

const groupBy = (containers, keyOf) => {
 const groups = new Map();
 containers.forEach(container => {
  const key = keyOf(container);
  if (!groups.has(key)) groups.set(key, []);
  groups.get(key).push(container);
 });
 return groups;
};
export const groupByProject = containers => groupBy(containers, container => container.project || null);
export const groupBySource = containers => groupBy(containers, container => container.sourceId);

const sortedProjects = projects => [...projects].sort((a, b) => a.localeCompare(b));

export const buildSections = ({ sources, containers, groupBy: mode, hostFilter = 'all', query = '' }) => {
 const visibleSources = hostFilter === 'all' ? sources : sources.filter(source => source.id === hostFilter);
 const sourceById = new Map(visibleSources.map(source => [source.id, source]));
 const visible = containers.filter(container => {
  const source = sourceById.get(container.sourceId);
  return source && matchesFilter(container, query, source.name);
 });
 const filtering = query.trim() !== '';

 if (mode === GROUP_BY.STACK) {
  const byProject = groupByProject(visible);
  const projects = sortedProjects([...byProject.keys()].filter(project => project !== null));
  const keys = byProject.has(null) ? [null, ...projects] : projects;
  return keys.map(project => {
   const items = byProject.get(project);
   const sourceIds = new Set(items.map(container => container.sourceId));
   return {
    id: 'stack:' + (project ?? ''),
    kind: 'stack',
    title: project ?? 'Standalone',
    source: null,
    hostNames: visibleSources.filter(source => sourceIds.has(source.id)).map(source => source.name),
    containers: items,
    groups: [{ key: project, label: null, containers: items }],
   };
  });
 }

 const bySource = groupBySource(visible);
 return visibleSources
  .map(source => {
   const items = bySource.get(source.id) ?? [];
   let groups;
   if (mode === GROUP_BY.HOST) {
    groups = [{ key: null, label: null, containers: items }];
   } else {
    const byProject = groupByProject(items);
    const projects = sortedProjects([...byProject.keys()].filter(project => project !== null));
    groups = (byProject.has(null) ? [null, ...projects] : projects).map(project => ({
     key: project, label: project, containers: byProject.get(project),
    }));
   }
   return { id: source.id, kind: 'host', title: source.name, source, hostNames: [source.name], containers: items, groups };
  })
  .filter(section => !filtering || section.containers.length > 0);
};
