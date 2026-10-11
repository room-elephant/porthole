/**
 * Groups containers by their project name.
 * Containers without a project are grouped under null.
 */
export const groupByProject = (containers) => {
  const groups = {};
  containers.forEach(container => {
    const project = container.project || null;
    if (!groups[project]) {
      groups[project] = [];
    }
    groups[project].push(container);
  });
  return groups;
};

