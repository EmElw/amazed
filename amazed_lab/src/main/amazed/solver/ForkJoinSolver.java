package amazed.solver;

import amazed.maze.Maze;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */


public class ForkJoinSolver
        extends SequentialSolver {

    private static final long SLEEP_CONSTANT = 20;
    private List<ForkJoinSolver> children;

    private ForkJoinSolver(Maze maze) {
        super( maze );
    }

    private ForkJoinSolver(Maze maze, int forkAfter, Set<Integer> visited) {
        this( maze );
        this.forkAfter = forkAfter;
        this.visited = visited;     // overwrites the default set init:ed in SequentialSolver
        this.children = new ArrayList<>();
    }

    public ForkJoinSolver(Maze maze, int forkAfter) {
        this( maze, forkAfter, new ConcurrentSkipListSet<>() );
    }

    @Override
    public List<Integer> compute() {
        if ( frontier.isEmpty() )     // only empty if it's the root solver
        {
            frontier.push( maze.start() );
        }
        List<Integer> result = parallelSearch();
        killChildren();
        return result;
    }

    /**
     * @return a list of node id:s from (local) start to goal
     */
    private List<Integer> parallelSearch() {

        int start = frontier.peek();
        int player = maze.newPlayer( start );

        // invariants: everything in frontier is mapped in predecessors
        while (!frontier.isEmpty()) {
            if ( frontier.size() > 1 ) {
                while (frontier.size() > 0) {
                    spawnChild( frontier.pop() );
                }
                List<Integer> result = joinChildren( start );
                if ( result != null ) {
                    return result;
                }
            } else {
                int currentNode = frontier.pop();

                if ( visited.add( currentNode ) ) { // returns false if the node is already in the set
                    maze.move( player, currentNode );
                    evaluateNode( currentNode );
                    if ( maze.hasGoal( currentNode ) ) {
                        List<Integer> result = pathFromTo( start, currentNode );
                        return result;
                    }
                }
            }
        }
        return null;

    }

    private void killChildren() {
        for (ForkJoinSolver child : children) {
            child.cancel( true );
        }
    }

    private List<Integer> joinChildren(int node) {
        for (ForkJoinSolver child : children) {
            List<Integer> childResult = child.join();
            if ( childResult != null ) {
                List<Integer> result = pathFromTo( node, childResult.get( 0 ) );
                assert result != null;
                result.remove( result.size() - 1 ); // remove last to remove "overlap"
                result.addAll( childResult );
                return result;
            }
        }
        return null;
    }

    private void spawnChild(int node) {
        ForkJoinSolver child = new ForkJoinSolver( maze, forkAfter, visited );
        child.frontier.push( node );
        children.add( child );
        child.fork();
    }

    private void addPredecessor(int from, int to) {
        predecessor.put( from, to );
    }

    private void evaluateNode(int evalNode) {
        for (Integer node : maze.neighbors( evalNode )) {
            if ( !visited.contains( node ) ) {
                frontier.push( node );
                addPredecessor( node, evalNode );
            }
        }
    }

}
