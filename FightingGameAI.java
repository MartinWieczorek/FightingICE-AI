import gameInterface.AIInterface;
import structs.CharacterData;
import structs.FrameData;
import structs.GameData;
import structs.Key;
import structs.MotionData;
import commandcenter.CommandCenter;
import enumerate.Action;
import enumerate.State;
import fighting.Attack;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

// test git branch

public class FightingGameAI implements AIInterface {

	private Key inputKey;
	private boolean player;
	private FrameData frameData;
	private CommandCenter commandCenter;
	
	private List<Enum<Action>> pastActions;
	private List<Enum<Action>> pastEnemyActions;
	private Action[] nearActions;
	private Action[] farActions;
	private Action[] moveActions;
	private float[][] actionValue;
	private float[][] actionProbability;
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
		
		nearActions = new Action[] {Action.STAND_D_DB_BA, Action.THROW_A, Action.THROW_B, Action.STAND_A, Action.STAND_B,
                Action.CROUCH_A, Action.CROUCH_B, Action.STAND_FA, Action.STAND_FB, Action.CROUCH_FA,
                Action.CROUCH_FB};
		
		farActions = new Action[] { Action.STAND_D_DF_FA, Action.STAND_D_DF_FB};
		
		moveActions = new Action[] {Action.BACK_STEP, Action.FORWARD_WALK, Action.DASH, Action.JUMP, Action.FOR_JUMP, Action.BACK_JUMP};

		actionValue = new float[Action.values().length][Action.values().length];	// rows = myActions; cols = enemyActions
		actionProbability = new float[Action.values().length][Action.values().length];
		float prob = 1.0f / actionProbability.length;
		for (int i = 0; i < actionValue.length; i++){
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
				commandCenter.skillCancel();
				
				if(detectHPdiff(myCharacter, oppCharacter)){
					currentScore = calcScore(myCharacter, oppCharacter);
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
				
				if(energy >= 300){
					commandCenter.commandCall( Action.STAND_D_DF_FC.name() );
				}else
					commandCenter.commandCall( chosenAction );
			}
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
		float discountValue = 0.5f;
		float discountFaktor = 1.0f;
		
		if(!pastActions.isEmpty()){
			for (int i = 0; i < pastActions.size(); i++){
				Enum<Action> myAction = pastActions.get(i);
				Enum<Action> enemyAction = pastEnemyActions.get(i);

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
			index = roulletWheel(farActions);

		}
		else if(distance < 180){
			// close combat
			index = roulletWheel(nearActions);
		}

		// list next action for scoring
		pastActions.add(Action.values()[index]);
		pastEnemyActions.add(oppCharacter.getAction());
		return Action.values()[index].name();
	}
	
	private int roulletWheel(Action[] actionSet)
	{
		// find all skills for which enough energy is available
		Action[] reducedActionSet = null;
		List<Enum<Action>> actions = new ArrayList<>();
		for (int i = 0; i < actionSet.length; i++){
			if(myCharacter.getEnergy() >= -myMotionData.elementAt(actionSet[i].ordinal()).attackStartAddEnergy)
				actions.add(actionSet[i]);
		}
		reducedActionSet = actions.toArray(new Action[0]);
		
		if(reducedActionSet.length == 0){
			return Action.FOR_JUMP.ordinal(); //if no action remains, then jump_forward
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
