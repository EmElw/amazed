package amazed.solver;

import amazed.maze.Maze;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

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

    static int itCount = 0;

    private static final long SLEEP_CONSTANT = 20;
    private List<ForkJoinSolver> children;
    private boolean root = false;
    private AtomicBoolean done;

    private ForkJoinSolver(Maze maze) {
        super( maze );
    }

    private ForkJoinSolver(Maze maze, int forkAfter, Set<Integer> visited, AtomicBoolean done) {
        this( maze );
        this.forkAfter = forkAfter;
        this.visited = visited;     // overwrites the default set init:ed in SequentialSolver
        this.children = new ArrayList<>();
        this.done = done;
    }

    public ForkJoinSolver(Maze maze, int forkAfter) {
        this( maze, forkAfter, new ConcurrentSkipListSet<>(), new AtomicBoolean( false ) );
    }

    @Override
    public List<Integer> compute() {
        if ( frontier.isEmpty() )     // only empty if it's the root solver
        {
            frontier.push( maze.start() );
            root = true;
        }
        List<Integer> result = parallelSearch();
        return result;
    }

    /**
     * @return a list of node id:s from (local) start to goal
     */
    private List<Integer> parallelSearch() {
        int forkCount = 0;
        int start = frontier.peek();
        int player = maze.newPlayer( start );

        // invariants: everything in frontier is mapped in predecessors
        while (!frontier.isEmpty()) {
            if ( done.get() ) {
                break;    // early termination when someone finds a solution
            }

            itCount++;

//            // spawn as soon as there are multiple options
//            if ( frontier.size() > 1 ) {
//                while (frontier.size() > 1) {
//                    spawnChild( frontier.pop() );
//                }
//            }
            if ( forkCount >= forkAfter ) {
                if ( frontier.size() > 1 ) {
                    spawnChild( frontier.pop() );
                    forkCount = 0;
                }
            } else {
                forkCount++;
            }
            int currentNode = frontier.pop();

            if ( visited.add( currentNode ) ) { // returns false if the node is already in the set
                maze.move( player, currentNode );
                evaluateNode( currentNode );
                if ( maze.hasGoal( currentNode ) ) {
                    done.set( true );
                    return pathFromTo( start, currentNode );
                }
            }

        }

        return joinChildren( start );
    }


    /**
     * Concatenates this (the parent's) path with the child's
     *
     * @param localstart  this instance local start
     * @param childResult the child's path (return value)
     * @return a path
     */
    private List<Integer> aggregateResult(int localstart, List<Integer> childResult) {
        List<Integer> result = pathFromTo( localstart, childResult.get( 0 ) );
        assert result != null;
        result.remove( result.size() - 1 ); // remove last to remove "overlap"
        result.addAll( childResult );
        return result;
    }

    /**
     * joins() all children and returns the first non-null result, or null if there is none
     *
     * @param node the local starting node of this instance (the parent)
     * @return a list of nodes representing a path, or null if there is no path
     */
    private List<Integer> joinChildren(int node) {
        for (ForkJoinSolver child : children) {
            List<Integer> childResult = child.join();
            if ( childResult != null ) {
                return aggregateResult( node, childResult );
            }
        }
        return null;
    }

    private void spawnChild(int node) {
        ForkJoinSolver child = new ForkJoinSolver( maze, forkAfter, visited, done );
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
