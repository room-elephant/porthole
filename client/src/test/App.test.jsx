import {describe,it,expect,vi,beforeEach} from 'vitest';
import {screen,within,fireEvent} from '@testing-library/react';
import App from '../App';
import {renderWithQuery,createMockContainer} from './testUtils';
import {useContainers} from '../hooks/useContainers';
vi.mock('../hooks/useContainers');
const walker={id:'walker',name:'Walker',status:'UP',error:null};
const mike={id:'mike',name:'Mike',status:'UP',error:null};
const query = data => useContainers.mockReturnValue({data,isLoading:false,error:null});
describe('App', () => {
 beforeEach(() => {vi.clearAllMocks();localStorage.clear();});
 it('shows six loading placeholders and the title', () => {
  useContainers.mockReturnValue({isLoading:true});renderWithQuery(<App/>);
  expect(screen.getByText('Porthole')).toBeInTheDocument();expect(document.querySelectorAll('.skeleton-card')).toHaveLength(6);
 });
 it('reports global fetch errors without identifying a Docker host as down', () => {
  useContainers.mockReturnValue({error:{response:{status:502}},isLoading:false});renderWithQuery(<App/>);
  expect(screen.getByRole('alert')).toHaveTextContent('Failed to fetch containers');
  expect(screen.queryByText('Docker unavailable')).not.toBeInTheDocument();
 });
 it('groups matching names and projects under their owning host', () => {
  query({sources:[walker,mike],containers:[createMockContainer({id:'same',sourceId:'walker',project:'shared',displayName:'Walker app'}),createMockContainer({id:'same',sourceId:'mike',project:'shared',displayName:'Mike app'})]});
  renderWithQuery(<App/>);
  const w=screen.getByRole('region',{name:'Walker'}),m=screen.getByRole('region',{name:'Mike'});
  expect(within(w).getByText('Walker app')).toBeInTheDocument();expect(within(w).queryByText('Mike app')).not.toBeInTheDocument();
  expect(within(m).getByText('Mike app')).toBeInTheDocument();expect(screen.getAllByRole('heading',{name:'shared'})).toHaveLength(2);
 });
 it('keeps healthy containers when another source is unavailable and shows recovery', () => {
  query({sources:[walker,{...mike,status:'DOWN',error:'Docker is not reachable'}],containers:[createMockContainer({sourceId:'walker'})]});
  const view=renderWithQuery(<App/>);
  expect(screen.getByText('Test Container')).toBeInTheDocument();expect(within(screen.getByRole('region',{name:'Mike'})).getByRole('alert')).toHaveTextContent('Docker unavailable');
  query({sources:[walker,mike],containers:[createMockContainer({sourceId:'mike',displayName:'Recovered'})]});view.rerender(<App/>);
  expect(screen.getByText('Recovered')).toBeInTheDocument();expect(screen.queryByText('Docker unavailable')).not.toBeInTheDocument();
 });
 it('shows connected empty hosts with filter guidance', () => {query({sources:[walker],containers:[]});renderWithQuery(<App/>);expect(screen.getByText('No containers found')).toBeInTheDocument();expect(screen.getByText(/No running containers with endpoints/)).toBeInTheDocument();});
 it('shows all unavailable hosts without inventing an empty success', () => {query({sources:[{...walker,status:'DOWN'},{...mike,status:'DOWN'}],containers:[]});renderWithQuery(<App/>);expect(screen.getAllByText('Docker unavailable')).toHaveLength(2);expect(screen.queryByText('No containers found')).not.toBeInTheDocument();});
 it('settings show every source and filters apply to the combined query', () => {query({sources:[walker,{...mike,status:'DOWN'}],containers:[]});renderWithQuery(<App/>);fireEvent.click(screen.getByTitle('Settings'));expect(screen.getByText('Walker: Connected')).toBeInTheDocument();expect(screen.getByText('Mike: Unavailable')).toBeInTheDocument();fireEvent.click(screen.getByLabelText('Show stopped containers'));expect(useContainers).toHaveBeenLastCalledWith({showAll:false,showStopped:true});});
});
