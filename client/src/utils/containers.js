import {EXTERNAL,GROUP_BY} from '../constants';
import {matchesFilter,matchesLinkFilter} from './filter';
import {sortLinks} from './links';

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

/** Standalone (null) first, then stacks by name. */
const orderedProjects = keys => {
 const unique = new Set(keys);
 const projects = sortedProjects([...unique].filter(project => project !== null));
 return unique.has(null) ? [null, ...projects] : projects;
};

const externalSection = (links, mode) => {
 const byStack = groupBy(links, link => link.stack ?? null);
 const groups = mode === GROUP_BY.HOST
  ? [{ key: null, label: null, containers: [], links }]
  : orderedProjects(byStack.keys()).map(stack => ({ key: stack, label: stack, containers: [], links: byStack.get(stack) }));
 return { id: EXTERNAL.ID, kind: 'external', title: EXTERNAL.NAME, source: null, hostNames: [EXTERNAL.NAME], containers: [], links, groups };
};

export const buildSections = ({ sources, containers, links = [], groupBy: mode, hostFilter = 'all', query = '' }) => {
 const visibleSources = hostFilter === 'all' ? sources : sources.filter(source => source.id === hostFilter);
 const sourceById = new Map(visibleSources.map(source => [source.id, source]));
 const visible = containers.filter(container => {
  const source = sourceById.get(container.sourceId);
  return source && matchesFilter(container, query, source.name);
 });
 const showLinks = hostFilter === 'all' || hostFilter === EXTERNAL.ID;
 const visibleLinks = showLinks ? sortLinks(links.filter(link => matchesLinkFilter(link, query))) : [];
 const filtering = query.trim() !== '';

 if (mode === GROUP_BY.STACK) {
  const byProject = groupByProject(visible);
  const linksByStack = groupBy(visibleLinks, link => link.stack ?? null);
  return orderedProjects([...byProject.keys(), ...linksByStack.keys()]).map(project => {
   const items = byProject.get(project) ?? [];
   const stackLinks = linksByStack.get(project) ?? [];
   const sourceIds = new Set(items.map(container => container.sourceId));
   return {
    id: 'stack:' + (project ?? ''),
    kind: 'stack',
    title: project ?? 'Standalone',
    source: null,
    hostNames: [
     ...visibleSources.filter(source => sourceIds.has(source.id)).map(source => source.name),
     ...(stackLinks.length > 0 ? [EXTERNAL.NAME] : []),
    ],
    containers: items,
    links: stackLinks,
    groups: [{ key: project, label: null, containers: items, links: stackLinks }],
   };
  });
 }

 const bySource = groupBySource(visible);
 const hosts = visibleSources
  .map(source => {
   const items = bySource.get(source.id) ?? [];
   let groups;
   if (mode === GROUP_BY.HOST) {
    groups = [{ key: null, label: null, containers: items, links: [] }];
   } else {
    const byProject = groupByProject(items);
    groups = orderedProjects(byProject.keys()).map(project => ({
     key: project, label: project, containers: byProject.get(project), links: [],
    }));
   }
   return { id: source.id, kind: 'host', title: source.name, source, hostNames: [source.name], containers: items, links: [], groups };
  })
  .filter(section => !filtering || section.containers.length > 0);
 return visibleLinks.length > 0 ? [...hosts, externalSection(visibleLinks, mode)] : hosts;
};

export const plural = (count, noun) => `${count} ${noun}${count === 1 ? '' : 's'}`;
