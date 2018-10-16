package amazed.solver;

import amazed.maze.Maze;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;

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

    public ForkJoinSolver(Maze maze) {
        super( maze );
    }

    public ForkJoinSolver(Maze maze, int forkAfter, Set<Integer> visited) {
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
            frontier.push( maze.start() );
        return parallelSearch();
    }

    /**
     * Rationale: uses classic DFS, but every forkAfter:th iteration
     * instead spawns a child to a frontier that works identically
     * from that location.
     * <p>
     * Continuous working when spawning children:
     * yes
     * Uses forkAfter:
     * yes
     *
     * @return a list of node id:s from (local) start to goal
     */
    private List<Integer> parallelSearch() {
        int forkAfterCounter = 0;
        int localStart = frontier.peek();
        int player = maze.newPlayer( localStart );
        while (!frontier.empty()) {
            int currentNode = frontier.pop();

            if ( maze.hasGoal( currentNode ) ) {
                List<Integer> result = pathFromTo( localStart, currentNode );
                assert result != null;
                return result;
            }

            // TODO investigate if forking should be impossible if the frontier is of size 1 (or less)
            // TODO investigate strategy to fork to every node in frontier (or every node except 1)
            if ( forkAfterCounter >= 1 && frontier.size() > 0 ) {      // fork every n iterations
                spawnChild( currentNode );
                forkAfterCounter = -1;
            } else if ( visited.add( currentNode ) ) {  // returns false if the element is already in the set, can happen if the node is visited by other process
                maze.move( player, currentNode );
                evaluateNode( currentNode );
            }

            // check in on children TODO might be redundant to do every tick
//            for (ForkJoinSolver child : children) {
//                List<Integer> result = evaluateChild( localStart, child );
//                if ( result != null ) return result;
//            }

            // increment
            forkAfterCounter++;
        }

        // wait on children to complete
        List<Integer> childResult = joinChildren( localStart );
        if ( childResult == null )
            return null;

        List<Integer> result = pathFromTo( localStart, childResult.get( 0 ) );
        result.addAll( childResult );
        return result;
    }

    /**
     * Checks if a child is done, and if so, returns that child's result
     * Otherwise returns null
     */
    private List<Integer> evaluateChild(int localStart, ForkJoinSolver child) {
        if ( child.isDone() ) {
            children.remove( child ); // remove from active children
            List<Integer> childResult = child.join();
            if ( childResult != null ) {
                List<Integer> result = pathFromTo( localStart, childResult.get( 0 ) );
                assert result != null;
                result.addAll( childResult ); // appends child's result, something like: A -> B ++ B -> goal
                return result;
            }
        }
        return null;
    }


    /**
     * Joins spawned child processes and returns the value
     * of the first non-null result or null if there is
     * no such result
     */
    private List<Integer> joinChildren(int localStart) {

        System.out.println( "Joining children... " + Thread.currentThread() );
        // Ver A (sequential join)
        for (ForkJoinSolver child : children) {
            List<Integer> result = null;
            try {
                result = child.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            System.out.println( " - result " + Thread.currentThread() );
            if ( result != null ) {
                return result;
            }
        }

        // Ver B (polling-ish join)
//        while (!children.isEmpty()) {
//            for (ForkJoinSolver child : children) {
//                List<Integer> result = evaluateChild(localStart, child);
//                if (result != null) return result;
//            }
//            try {
//                wait(SLEEP_CONSTANT);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }

        return null;
    }

    /**
     * Evaluates a node, adding any non-visited neighbours to the frontier
     * as well as constructing the predecessor path
     */
    private void evaluateNode(int node) {
        Set<Integer> neighbours = maze.neighbors( node );

        neighbours.forEach( nextNode -> {
            if ( !visited.contains( nextNode ) ) {
                predecessor.put( nextNode, node );
                frontier.push( nextNode );
            }
        } );
    }

    /**
     * Spawns a child solver, starting at a given node
     * NOTE: Adds it to the list of children
     */
    private void spawnChild(int currentNode) {
        ForkJoinSolver c = new ForkJoinSolver( maze, forkAfter, visited );
        children.add( c );
        c.frontier.push( currentNode );       // set starting point
        c.fork();                           // starts the child process
    }
}
