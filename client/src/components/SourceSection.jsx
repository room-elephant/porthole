import { Package, AlertTriangle } from 'lucide-react';
import ContainerTile from './ContainerTile';
import { groupByProject } from '../utils/containers';
export default function SourceSection({source, containers, showAll, showStopped, allowLegacyMigration}) {
 const groups = groupByProject(containers);
 const projects = [...groups.keys()].filter(key => key !== null).sort();
 const connected = source.status === 'UP';
 const tiles = list => <div className="container-grid">{list.map(container => <ContainerTile key={JSON.stringify([container.sourceId,container.id])} container={container} allowLegacyMigration={allowLegacyMigration}/>)}</div>;
 const emptyMessage = !showAll && !showStopped
  ? 'No running containers with endpoints. Try enabling "Show stopped containers" or "Show containers without endpoints" in settings.'
  : !showStopped ? 'No containers with endpoints found. Try enabling "Show stopped containers" in settings.'
  : !showAll ? 'No running containers found. Try enabling "Show containers without endpoints" in settings.'
  : 'Docker is running but no containers exist. Start some containers to see them here.';
 return <section className="source-section" aria-labelledby={`source-${source.id}`}>
  <header className="source-header"><h2 id={`source-${source.id}`} className="source-title">{source.name}</h2>
   <span className="source-status"><span className={`status-indicator ${connected ? 'status-running' : 'status-stopped'}`}/>{connected ? 'Connected' : source.status === 'DOWN' ? 'Unavailable' : 'Error'}{connected && <span className="source-count">{containers.length} {containers.length === 1 ? 'container' : 'containers'}</span>}</span>
  </header>
  {!connected ? <div className="empty-state warning" role="alert"><AlertTriangle size={32}/><h3>{source.status === 'DOWN' ? 'Docker unavailable' : 'Could not load containers'}</h3><p>{source.error}</p><p>Retrying automatically.</p></div>
   : containers.length === 0 ? <div className="empty-state"><Package size={40} strokeWidth={1}/><h3>No containers found</h3><p>{emptyMessage}</p></div>
   : <>{groups.has(null) && tiles(groups.get(null))}{projects.map(project => <div key={project} className="project-section"><h3 className="project-title">{project}</h3>{tiles(groups.get(project))}</div>)}</>}
 </section>;
}
