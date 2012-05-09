package player.gamer.statemachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import player.gamer.statemachine.HeuristicGamerSM.Move_Node;

import util.statemachine.MachineState;
import util.statemachine.Move;
import util.statemachine.Role;
import util.statemachine.StateMachine;
import util.statemachine.exceptions.GoalDefinitionException;
import util.statemachine.exceptions.MoveDefinitionException;
import util.statemachine.exceptions.TransitionDefinitionException;
import util.statemachine.implementation.prover.ProverStateMachine;

public abstract class HeuristicGamer extends StateMachineGamer {
	//defines the heuristic used to value a state
	public abstract double getHeuristic(MachineState state, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException;
	public abstract double getHeuristicPOST(MachineState state, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException;

	//names the player
	public abstract String getName();

	private static final int MAX_SCORE = 100;
	private static final int STATES_TO_EXPAND = 64;
	private static final int CALCULATION_BUFFER = 1000;

	private List<Role> _opp_roles;
	private HashMap< MachineState,HashMap< List<Move>, MachineState> > _stateCache;
	private HashMap<MachineState, Integer> _cache;
	private HashMap<MachineState, Move_Node> _maxCache;
	private long _nodesExpanded;
	protected long _stopTime;
	private long _cacheHit;
	private int _numStatesExpanded;
	private int _levelsToExpand;

	private class Move_Node {
		public double value;
		public Move move;
		public Move_Node(int value, Move move){
			this.value = value;
			this.move = move;
		}
	}

	@Override
	public StateMachine getInitialStateMachine() {	
		return new ProverStateMachine();
	}

	@Override
	public void stateMachineAbort() {}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		_opp_roles = new ArrayList<Role>();
		_stateCache = new HashMap<MachineState, HashMap< List<Move>, MachineState> >();
		_maxCache = new HashMap<MachineState, Move_Node>();
		_cache = new HashMap<MachineState, Integer>();
		_nodesExpanded = 0;
		ArrayList<Role> all_roles = (ArrayList<Role>) getStateMachine().getRoles();
		for(int i=0; i<all_roles.size(); i++){
			if(!(getRole().equals(all_roles.get(i)))){
				_opp_roles.add(all_roles.get(i));
			}
		}
		_stopTime = timeout-500;
		/* ***************NOT DOING METAGAMING YET************************/
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {

		_stopTime = timeout - 1000;
		_numStatesExpanded = 0;



		Move move = null;
		//		while (_numStatesExpanded < STATES_TO_EXPAND) {
		//			move = getMove(getCurrentState(),_stopTime);
		//			System.out.println("still not enough states " + _numStatesExpanded);
		//			System.out.println("expanded to level " + _levelsToExpand);
		//			_levelsToExpand++;
		//			break;
		//		}
		//		if(move == null){
		//			System.out.println("FAILED TO GET MOVE");
		//			move = getStateMachine().getRandomMove(getCurrentState(), getRole());
		//		}

		MachineState currState = getCurrentState();
		List<Move>  legalMoves= getStateMachine().getLegalMoves(currState, getRole());

		long currTime = System.currentTimeMillis();
		long timeAllocatedPRE = (timeout-currTime)/4; //DETERMINES THE TIME ALLOCATED FOR PRE STUFF
		System.out.println("Time ALlocated:" + timeAllocatedPRE);
		_levelsToExpand = 2;
		List<Double> moveScores = getMove(currState, currTime + timeAllocatedPRE, legalMoves);
		System.out.println("Pre->Post");
		_levelsToExpand = 0;
		List<Double> moveScoresPOST = getMovePOST(currState, timeout, legalMoves);


		if(moveScores.isEmpty()&&moveScoresPOST.isEmpty()){
			System.out.println("FAILED TO GET MOVE");
			move = getStateMachine().getRandomMove(getCurrentState(), getRole());
		}


		List<Double> finalScore = new ArrayList<Double>();
		for(int i = 0; i<legalMoves.size(); i++){
			double score = 0;
			if(moveScores.size()<i-1){
				score+=0;
			} else {
				score += (moveScores.get(i) * 2)/5;    //RANDOM RATIO FOR HEURISTICS VS MONTE
			}
			if(moveScoresPOST.size()<i-1){
				score+=0;
			} else {
				score += ((moveScoresPOST.get(i)+100) * 3)/5;  //THIS IS THE MONTE RATIO PART
			}
			finalScore.add(score);
		}
		int index = 0;
		double bestscore = 0;
		for(int i = 0; i<legalMoves.size(); i++){
			if(finalScore.get(i)>bestscore){
				index = i;
				bestscore = finalScore.get(i);
			}
			System.out.println("pre "+i+" : " + moveScores.get(i));
			System.out.println("post "+i+" : " + moveScoresPOST.get(i));
			System.out.println("score "+i+" : " + finalScore.get(i));

		}


		return legalMoves.get(index);
	}	

	/*
	 * Selects the move with the highest minimax value from the available moves
	 * for this state.  This is essentially a copy of maxScore which tracks moves.
	 */
	private List<Double> getMove(MachineState currState, long timeout, List<Move> legalMoves) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException{
		if(getStateMachine().isTerminal(currState)){
			//return getStateMachine().getRandomMove(currState, getRole());
			return new ArrayList<Double>(0);
		}
		Move_Node maxNode = new Move_Node(-1, null);


		List<Double> ScoreList = new ArrayList<Double>();
		//List<Move>  legalMoves= getStateMachine().getLegalMoves(currState, getRole());
		long curr_time = System.currentTimeMillis();
		long time_step = (timeout-curr_time)/legalMoves.size();

		for(int i = 0; i<legalMoves.size(); i++){  //PREPROCESSING (IE NON-MONTE CARLO HEURISTICS)
			Move move = legalMoves.get(i);
			double tempVal = minScore(getRole(), move, currState, 0, curr_time + (i+1)*time_step);
			ScoreList.add(tempVal);
			if(tempVal > maxNode.value){
				maxNode.value = tempVal;	
				maxNode.move = move;
			}
		}



		return ScoreList;
		//	return maxNode.move;
	}

	/*
	 * Function to control the depth of expansion.  
	 * Currently not very interesting.
	 */
	private boolean stopExpanding(MachineState state, int level){
		return (_numStatesExpanded >= STATES_TO_EXPAND)|| (System.currentTimeMillis() > _stopTime);
	}
	/*
	 * Gets the max possible score for a given role at this state
	 * Level is the number of times to allow recursion, at this level
	 * we use the heuristic value of the state.
	 */
	private double maxScore(Role role, MachineState state, int level, long timeout) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException{
		if(getStateMachine().isTerminal(state)){ //base case 
			System.out.println("terminal");
			//return getStateMachine().getGoal(state, role);
			return getRelGoal(state);
		}
		else if(stopExpanding(state, level)) {
			System.out.println("heuristic");

			return getHeuristic(state, timeout); 
		}
		if (level > _levelsToExpand) {
			System.out.println("levels");

			return getHeuristic(state, timeout); 
		}

		if(System.currentTimeMillis() >= timeout-CALCULATION_BUFFER) return getHeuristic(state, timeout);

		double maxVal = 0;
		//choose the move with the max value against a rational player

		List<Move>  legalMoves= getStateMachine().getLegalMoves(state, getRole());
		long curr_time = System.currentTimeMillis();
		long time_step = (timeout-curr_time)/legalMoves.size();

		System.out.println("timestep_inmax: "+time_step);


		for(int i = 0; i<legalMoves.size(); i++){
			Move move = legalMoves.get(i);
			double tempVal = minScore(role, move, state, level, curr_time + (i+1)*time_step);

			if(System.currentTimeMillis() >= timeout - CALCULATION_BUFFER) return getHeuristic(state, timeout);

			if(tempVal > maxVal) maxVal = tempVal;			
		}
		return maxVal;
	}
	/*
	 * Gets the minimum score a rational opponent would allow to be the value at
	 * this state
	 */
	private double minScore(Role role, Move move, MachineState state, int level, long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException{
		double minVal = MAX_SCORE;
		//iterate over all possible joint moves, chose lowest scoring
		List<List<Move>>  jointMoves= getStateMachine().getLegalJointMoves(state, role, move);
		long curr_time = System.currentTimeMillis();
		long time_step = (timeout-curr_time)/jointMoves.size();

		System.out.println("timestep_inmin: "+time_step);

		for(int i = 0; i<jointMoves.size(); i++){
			List<Move > jointMove = jointMoves.get(i);
			//recursive step
			_numStatesExpanded++;
			double tempVal = maxScore(role, getStateMachine().getNextState(state, jointMove), level+1, curr_time + (i+1)*time_step);
			if(tempVal < minVal) minVal = tempVal;
		}
		return minVal;
	}









	/*
	 * Selects the move with the highest minimax value from the available moves
	 * for this state.  This is essentially a copy of maxScore which tracks moves.
	 */
	private List<Double> getMovePOST(MachineState currState, long timeout, List<Move> legalMoves) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException{
		if(getStateMachine().isTerminal(currState)){
			//return getStateMachine().getRandomMove(currState, getRole());
			return new ArrayList<Double>(0);
		}
		Move_Node maxNode = new Move_Node(-1, null);


		List<Double> ScoreList = new ArrayList<Double>();
		//List<Move>  legalMoves= getStateMachine().getLegalMoves(currState, getRole());
		long curr_time = System.currentTimeMillis();
		long time_step = (timeout-curr_time)/legalMoves.size();

		for(int i = 0; i<legalMoves.size(); i++){  //POSTPROCESSING (IE MONTE CARLO HEURISTICS)
			Move move = legalMoves.get(i);
			double tempVal = minScorePOST(getRole(), move, currState, 0, curr_time + (i+1)*time_step);
			ScoreList.add(tempVal);
			if(tempVal > maxNode.value){
				maxNode.value = tempVal;	
				maxNode.move = move;
			}
		}



		return ScoreList;
		//	return maxNode.move;
	}

	/*
	 * Function to control the depth of expansion.  
	 * Currently not very interesting.
	 */
	private boolean stopExpandingPOST(MachineState state, int level){
		return (_numStatesExpanded >= STATES_TO_EXPAND)|| (System.currentTimeMillis() > _stopTime);
	}
	/*
	 * Gets the max possible score for a given role at this state
	 * Level is the number of times to allow recursion, at this level
	 * we use the heuristic value of the state.
	 */
	private double maxScorePOST(Role role, MachineState state, int level, long timeout) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException{
		if(getStateMachine().isTerminal(state)){ //base case 
			System.out.println("terminal");
			//return getStateMachine().getGoal(state, role);
			return getRelGoal(state);

		}
		else if(stopExpanding(state, level)) {
			System.out.println("heuristic");

			return getHeuristicPOST(state, timeout); 
		}
		if (level > _levelsToExpand) {
			System.out.println("levels");

			return getHeuristicPOST(state, timeout); 
		}

		if(System.currentTimeMillis() >= timeout-CALCULATION_BUFFER) return getHeuristicPOST(state, timeout);

		double maxVal = 0;
		//choose the move with the max value against a rational player

		List<Move>  legalMoves= getStateMachine().getLegalMoves(state, getRole());
		long curr_time = System.currentTimeMillis();
		long time_step = (timeout-curr_time)/legalMoves.size();

		System.out.println("timestep_inmax: "+time_step);


		for(int i = 0; i<legalMoves.size(); i++){
			Move move = legalMoves.get(i);
			double tempVal = minScorePOST(role, move, state, level, curr_time + (i+1)*time_step);

			if(System.currentTimeMillis() >= timeout - CALCULATION_BUFFER) return getHeuristicPOST(state, timeout);

			if(tempVal > maxVal) maxVal = tempVal;			
		}
		return maxVal;
	}
	/*
	 * Gets the minimum score a rational opponent would allow to be the value at
	 * this state
	 */
	private double minScorePOST(Role role, Move move, MachineState state, int level, long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException{
		double minVal = MAX_SCORE;
		//iterate over all possible joint moves, chose lowest scoring
		List<List<Move>>  jointMoves= getStateMachine().getLegalJointMoves(state, role, move);
		long curr_time = System.currentTimeMillis();
		long time_step = (timeout-curr_time)/jointMoves.size();

		System.out.println("timestep_inmin: "+time_step);

		for(int i = 0; i<jointMoves.size(); i++){
			List<Move > jointMove = jointMoves.get(i);
			//recursive step
			_numStatesExpanded++;
			double tempVal = maxScorePOST(role, getStateMachine().getNextState(state, jointMove), level+1, curr_time + (i+1)*time_step);
			if(tempVal < minVal) minVal = tempVal;
		}
		return minVal;
	}





public double getRelGoal(MachineState state) throws GoalDefinitionException{
	double mygoal = getStateMachine().getGoal(state, getRole());
	List<Integer> allgoals = getStateMachine().getGoals(state);
	double average=0;
	for(int i=0; i<allgoals.size(); i++){
		average+=allgoals.get(i);
	}
	average /= allgoals.size();
	return mygoal-average;
	
//	return _cacheHit;
}





	@Override
	public void stateMachineStop() {
		_cache.clear();
		_maxCache.clear();
	}
}