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
