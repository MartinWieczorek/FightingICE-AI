import gameInterface.AIInterface;
import structs.CharacterData;
import structs.FrameData;
import structs.GameData;
import structs.Key;
import structs.MotionData;
import commandcenter.CommandCenter;
import enumerate.Action;
import fighting.Attack;

import java.util.*;

// test git branch

public class FightingGameAI implements AIInterface {

	private Key inputKey;
	private boolean player;
	private FrameData frameData;
	private CommandCenter commandCenter;
	
	private List<Enum<Action>> pastActions;
	private List<Enum<Action>> pastEnemyActions;
	private Action[] groundActions;
	private Action[] airActions;
	private Action[] nearActions;
	private Action[] midActions;
	private Action[] farActions;
	private Action[] farActionsEnergy;
	private Action[] moveActions;
	private Action[] guardActions;
	private float[][] actionValue;
	private float[][] actionProbability;
	private int [] actionMaxRange;
	private int [] actionMinRange;
	int distance;
	
	/** self information */
	private CharacterData myCharacter;
	private Vector<MotionData> myMotionData;
	private int myHpLastFrame;

	/** opponent information */
	private CharacterData oppCharacter;
	private int oppHpLastFrame;
	
	/** global information */
	private int currentScore;	
	
	@Override
	public void close() 
	{
		// TODO Auto-generated method stub
		for (int i = 0; i < actionMaxRange.length; i++){
			System.out.println(Action.values()[i].name() + " has range: " + actionMinRange[i] + " - " + actionMaxRange[i]);
		}
	}

	@Override
	public String getCharacter() 
	{
		// TODO Auto-generated method stub
		return CHARACTER_ZEN;
	}

	@Override
	public void getInformation(FrameData frameData) 
	{
		// TODO Auto-generated method stub
		this.frameData = frameData;
		this.commandCenter.setFrameData(frameData, player);
		
		if (player) {
			myCharacter = frameData.getP1();
			oppCharacter = frameData.getP2();
		} else {
			myCharacter = frameData.getP2();
			oppCharacter = frameData.getP1();
		}
	}

	@Override
	public int initialize(GameData gameData, boolean player) 
	{
		// TODO Auto-generated method stub
		inputKey = new Key();
		this.player = player;  // own player (enemy is negated)
		frameData = new FrameData();
		commandCenter = new CommandCenter();
		myMotionData = gameData.getMyMotion(player);
		
		pastActions = new ArrayList<>();
		pastEnemyActions = new ArrayList<>();
		
		groundActions = new Action[] {Action.STAND_D_DB_BA, Action.BACK_STEP, Action.FORWARD_WALK, Action.DASH,
                Action.JUMP, Action.FOR_JUMP, Action.BACK_JUMP, Action.STAND_GUARD,
                Action.CROUCH_GUARD, Action.THROW_A, Action.THROW_B, Action.STAND_A, Action.STAND_B,
                Action.CROUCH_A, Action.CROUCH_B, Action.STAND_FA, Action.STAND_FB, Action.CROUCH_FA,
                Action.CROUCH_FB, Action.STAND_D_DF_FA, Action.STAND_D_DF_FB, Action.STAND_F_D_DFA,
                Action.STAND_F_D_DFB, Action.STAND_D_DB_BB};
		
		nearActions = new Action[] {Action.STAND_D_DB_BA, Action.THROW_A, Action.THROW_B, Action.STAND_A, Action.STAND_B,
                Action.CROUCH_A, Action.CROUCH_B, Action.STAND_FA, Action.STAND_FB, Action.CROUCH_FA,
                Action.CROUCH_FB};
		
		midActions = new Action[] {Action.CROUCH_FA, Action.CROUCH_FB};
		
		farActions = new Action[] { Action.STAND_D_DF_FA, Action.STAND_D_DF_FB};
		
		farActionsEnergy = new Action[] { Action.STAND_D_DF_FA};
		
		moveActions = new Action[] {Action.BACK_STEP, Action.FORWARD_WALK, Action.DASH, Action.JUMP, Action.FOR_JUMP, Action.BACK_JUMP};
		
		guardActions = new Action[] {Action.STAND_GUARD, Action.CROUCH_GUARD};
		
		airActions = new Action[] {Action.AIR_GUARD, Action.AIR_A, Action.AIR_B, Action.AIR_DA, Action.AIR_DB,
		                Action.AIR_FA, Action.AIR_FB, Action.AIR_UA, Action.AIR_UB, Action.AIR_D_DF_FA,
		                Action.AIR_D_DF_FB, Action.AIR_F_D_DFA, Action.AIR_F_D_DFB, Action.AIR_D_DB_BA,
		                Action.AIR_D_DB_BB};
		
		System.out.println("size of actions" + Action.values().length);

		actionValue = new float[Action.values().length][Action.values().length];	// rows = myActions; cols = enemyActions
		actionProbability = new float[Action.values().length][Action.values().length];
		actionMaxRange = new int[Action.values().length];
		actionMinRange = new int[Action.values().length];
		float prob = 1.0f / actionProbability.length;
		for (int i = 0; i < actionValue.length; i++){
			actionMaxRange[i] = 0;
			actionMinRange[i] = 1000;
			for (int j = 0; j < Action.values().length; j++){
				actionValue[i][j] = 0.5f;
				actionProbability[i][j] = prob;
			}			
		}
		
		
		myHpLastFrame = 0;
		oppHpLastFrame = 0;
		currentScore = 0;
		pastActions.clear();
		
		return 0;
	}

	@Override
	public Key input() 
	{
		// TODO Auto-generated method stub
		return inputKey;
	}

	@Override
	public void processing() 
	{
		// TODO Auto-generated method stub
		if(!frameData.getEmptyFlag() && frameData.getRemainingTime() > 0){
			if(commandCenter.getskillFlag()){
				inputKey = commandCenter.getSkillKey();
			}else{
				long startTime = System.nanoTime();
				//measureEnergyConsuption();
				// you find energy consuption in: 
				// myMotionData.elementAt(Action.AIR_D_DF_FB.ordinal()).attackStartAddEnergy
				
				if(detectHPdiff(myCharacter, oppCharacter)){
					calcLastActionsRange();
					System.out.println("hp diff");
					currentScore = calcScore(myCharacter, oppCharacter);
					//System.out.println("score: " + currentScore);
					updateActionValues((float) (currentScore * 0.01));
				}
				
				int energy = myCharacter.getEnergy();
				distance = commandCenter.getDistanceX();
				
				String chosenAction;
				// move or action (20:80)
				float randomNumber = new Random().nextFloat();
				if(randomNumber < 0.1){
					chosenAction = chooseMovement();
				}
				else{
					chosenAction = chooseAction();
				}
				//System.out.println(chosenAction);
				
				if(energy >= 300){
					commandCenter.commandCall( Action.STAND_D_DF_FC.name() );
				}else
					commandCenter.commandCall( chosenAction );
				
				long endtime = System.nanoTime();
				//System.out.println("time needed: " + ((endtime - startTime)/1e6));
			}
		}
	}
	
	private void calcLastActionsRange()
	{
		if(distance > actionMaxRange[pastActions.get(pastActions.size()-1).ordinal()] && oppCharacter.hp != oppHpLastFrame){
			actionMaxRange[pastActions.get(pastActions.size()-1).ordinal()] = distance;
		}
		if(distance < actionMinRange[pastActions.get(pastActions.size()-1).ordinal()] && oppCharacter.hp != oppHpLastFrame){
			actionMinRange[pastActions.get(pastActions.size()-1).ordinal()] = distance;
		}
	}
	
	private boolean detectHPdiff(CharacterData myCharacter, CharacterData oppCharacter)
	{
		boolean ret = false;
		if(myCharacter.hp != myHpLastFrame){
			ret = true;
		}
		if(oppCharacter.hp != oppHpLastFrame){
			ret = true;
		}
		return ret;
	}
	
	private int calcScore(CharacterData myCharacter, CharacterData oppCharacter)
	{
		int score = 0;
		
		int myDiff = myHpLastFrame - myCharacter.hp;
		int oppDiff = oppHpLastFrame - oppCharacter.hp;
		score = oppDiff - myDiff;
		
		myHpLastFrame = myCharacter.hp;
		oppHpLastFrame = oppCharacter.hp;
		
		return score;
	}
	
	private void updateActionValues(float score)
	{
		// TODO: limit actionValues from 0 to 1
		float discountValue = 0.5f;
		float discountFaktor = 1.0f;
		
		if(!pastActions.isEmpty()){
			for (int i = 0; i < pastActions.size(); i++){
				Enum<Action> myAction = pastActions.get(i);
				Enum<Action> enemyAction = pastEnemyActions.get(i);
				//System.out.println(index.ordinal());
				actionValue[myAction.ordinal()][enemyAction.ordinal()] += score * discountFaktor;
				if(actionValue[myAction.ordinal()][enemyAction.ordinal()] < 0)
					actionValue[myAction.ordinal()][enemyAction.ordinal()] = 0.0001f;
				if(actionValue[myAction.ordinal()][enemyAction.ordinal()] > 1)
					actionValue[myAction.ordinal()][enemyAction.ordinal()] = 1.0f;
				discountFaktor *= discountValue;
			}
		}
		
		pastActions.clear();
		pastEnemyActions.clear();
		//System.out.println(Arrays.toString(actionValue));
	}
	
	private void updateProbabilities(Action[] actionSet)
	{
		if(actionSet.length == 0)
			return;
		
		float summedScore = 0;
		Enum<Action> enemyAction = oppCharacter.getAction();
		
		for(int i = 0; i < actionSet.length; i++){
			summedScore += actionValue[actionSet[i].ordinal()][enemyAction.ordinal()];
		}
		
		// should not happen
		if(summedScore <= 0){
			summedScore = 1;
		}
		
		for(int i = 0; i < actionSet.length; i++){
			actionProbability[actionSet[i].ordinal()][enemyAction.ordinal()] = actionValue[actionSet[i].ordinal()][enemyAction.ordinal()] / summedScore;
		}
		
		//System.out.println(Arrays.toString(actionProbability));
		
	}
	
	private String chooseMovement()
	{
		int index = new Random().nextInt(moveActions.length);
		int chosenMovement = moveActions[index].ordinal();
		return Action.values()[chosenMovement].name();
	}
	
	private String chooseAction()
	{
		// select action based on probability
		int index = 0;
		int distance = commandCenter.getDistanceX();
		if(distance >= 180){
			// long distance
			//System.out.println("far distance: " + distance);
			index = roulletWheel(farActions);

		}
//		else if(distance < 300 && distance >= 150){
//			// mid range
//			//System.out.println("mid distance: " + distance);
//			index = roulletWheel(midActions);
//		}
		else if(distance < 180){
			// close combat
			//System.out.println("near distance: " + distance);
			index = roulletWheel(nearActions);
		}
		
		// list next action for scoring
		pastActions.add(Action.values()[index]);
		pastEnemyActions.add(oppCharacter.getAction());
		//System.out.println(Action.values()[index].name() + " " + index);
		return Action.values()[index].name();
	}
	
	private int roulletWheel(Action[] actionSet)
	{
		// find all skills for which enough energy is available
		Action[] reducedActionSet = null;
		List<Enum<Action>> actions = new ArrayList<>();
		for (int i = 0; i < actionSet.length; i++){
			//System.out.println("energy: " + myCharacter.energy + " needed: " + -myMotionData.elementAt(actionSet[i].ordinal()).attackStartAddEnergy);
			if(myCharacter.getEnergy() >= -myMotionData.elementAt(actionSet[i].ordinal()).attackStartAddEnergy)
				actions.add(actionSet[i]);
		}
		reducedActionSet = actions.toArray(new Action[0]);
		
		if(reducedActionSet.length == 0){
			int randomNumber = new Random().nextInt(56);
			return randomNumber; //if no action remains, then action guard
		}
			
		
		updateProbabilities(reducedActionSet);
		Enum<Action> enemyAction = oppCharacter.getAction();
		int index = 0;
		float summedProb = 0;
		float randomNumber = new Random().nextFloat();
		
		for (int i = 0; i < reducedActionSet.length; i++){
			summedProb += actionProbability[reducedActionSet[i].ordinal()][enemyAction.ordinal()];
			if(randomNumber < summedProb){
				index = i;
				break;
			}
		}
		return reducedActionSet[index].ordinal();
	}

}
