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
	private Action[] groundActions;
	private Action[] airActions;
	private Action[] attackActions;
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
	int numberGames;
	
	/** save/read data */
	File fileMaxRange;
	File fileMinRange;
	PrintWriter pw;
	BufferedReader br;
	
	/** self information */
	private CharacterData myCharacter;
	private Vector<MotionData> myMotionData;
	private int myHpLastFrame;
	private String myName;

	/** opponent information */
	private CharacterData oppCharacter;
	private int oppHpLastFrame;
	
	/** global information */
	private int currentScore;	
	
	@Override
	public void close() 
	{
		// TODO Auto-generated method stub
//		for (int i = 0; i < actionMaxRange.length; i++){
//			System.out.println(Action.values()[i].name() + " has range: " + actionMinRange[i] + " - " + actionMaxRange[i]);
//		}
//		System.out.println("numberGames: " + numberGames);
		
		// write data
		try {
			pw = new PrintWriter(fileMaxRange);
			for (int i = 0; i < actionMaxRange.length; i++){
				pw.println(actionMaxRange[i]);
			}
			pw.println(numberGames + 1);
			pw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			pw = new PrintWriter(fileMinRange);
			for (int i = 0; i < actionMinRange.length; i++){
				pw.println(actionMinRange[i]);
			}
			pw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
		myName = gameData.getMyName(player);
		
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
		
		airActions = new Action[] {Action.AIR_A, Action.AIR_B, Action.AIR_DA, Action.AIR_DB,
                Action.AIR_FA, Action.AIR_FB, Action.AIR_UA, Action.AIR_UB, Action.AIR_D_DF_FA,
                Action.AIR_D_DF_FB, Action.AIR_F_D_DFA, Action.AIR_F_D_DFB, Action.AIR_D_DB_BA,
                Action.AIR_D_DB_BB};
		
		attackActions = new Action[] {Action.STAND_D_DB_BA, Action.THROW_A, Action.THROW_B, Action.STAND_A, Action.STAND_B,
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
		
		numberGames = 0;
		myHpLastFrame = 0;
		oppHpLastFrame = 0;
		currentScore = 0;
		pastActions.clear();
		
		// read data from file
		fileMaxRange = new File("data/aiData/Singularity/" + myName + "maxRange.txt");
		fileMinRange = new File("data/aiData/Singularity/" + myName + "minRange.txt");
		
		try {
			br = new BufferedReader(new FileReader(fileMaxRange));
			for (int i = 0; i < actionMaxRange.length; i++){
				String line = br.readLine();
				actionMaxRange[i] = Integer.parseInt(line);
			}
			String line = br.readLine();
			numberGames = Integer.parseInt(line);
			br.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			br = new BufferedReader(new FileReader(fileMinRange));
			for (int i = 0; i < actionMinRange.length; i++){
				String line = br.readLine();
				actionMinRange[i] = Integer.parseInt(line);
				//System.out.println("data " + i + ":" + actionMinRange[i] + " - " + actionMaxRange[i]);
			}
			br.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
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
				
				commandCenter.skillCancel();
				
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
		if(pastActions.size() == 0)
			return;
		
		if(distance > actionMaxRange[pastActions.get(pastActions.size()-1).ordinal()] && oppCharacter.hp != oppHpLastFrame
				&& (distance < actionMaxRange[pastActions.get(pastActions.size()-1).ordinal()] + 100 || actionMaxRange[pastActions.get(pastActions.size()-1).ordinal()] == 0 )){
			actionMaxRange[pastActions.get(pastActions.size()-1).ordinal()] = distance;
		}
		if(distance < actionMinRange[pastActions.get(pastActions.size()-1).ordinal()] && oppCharacter.hp != oppHpLastFrame
				&& (distance > actionMinRange[pastActions.get(pastActions.size()-1).ordinal()] - 100 || actionMinRange[pastActions.get(pastActions.size()-1).ordinal()] == 1000)){
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
		float discountValue = 0.5f;
		float discountFaktor = 1.0f;
		//System.out.println("update score: " + score );
		
		if(!pastActions.isEmpty()){
			for (int i = 0; i < pastActions.size(); i++){
				Enum<Action> myAction = pastActions.get(i);
				Enum<Action> enemyAction = pastEnemyActions.get(i);
				//System.out.println("before update: " + actionValue[myAction.ordinal()][enemyAction.ordinal()]);
				actionValue[myAction.ordinal()][enemyAction.ordinal()] += score * discountFaktor;
				if(actionValue[myAction.ordinal()][enemyAction.ordinal()] < 0)
					actionValue[myAction.ordinal()][enemyAction.ordinal()] = 0.0001f;
				if(actionValue[myAction.ordinal()][enemyAction.ordinal()] > 1)
					actionValue[myAction.ordinal()][enemyAction.ordinal()] = 1.0f;
				discountFaktor *= discountValue;
				//System.out.println("after update: " + actionValue[myAction.ordinal()][enemyAction.ordinal()]);
			}
		}
		
		pastActions.clear();
		pastEnemyActions.clear();
		//printActionValues();
	}
	
	private void updateProbabilities(Action[] actionSet)
	{
		if(actionSet.length == 0)
			return;
		
		float summedScore = 0;
		Enum<Action> enemyAction = oppCharacter.getAction();
		
		for(int i = 0; i < actionSet.length; i++){
			//System.out.println("prob: " + actionValue[actionSet[i].ordinal()][enemyAction.ordinal()]);
			summedScore += actionValue[actionSet[i].ordinal()][enemyAction.ordinal()];
		}
		
		// should not happen
		if(summedScore <= 0){
			summedScore = 1;
		}
		
		for(int i = 0; i < actionSet.length; i++){
			actionProbability[actionSet[i].ordinal()][enemyAction.ordinal()] = actionValue[actionSet[i].ordinal()][enemyAction.ordinal()] / summedScore;
			System.out.println(actionSet[i].name() + ": " + actionProbability[actionSet[i].ordinal()][enemyAction.ordinal()]);
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
			System.out.println("far distance: " + distance);
			index = roulletWheel(farActions);

		}
//		else if(distance < 300 && distance >= 150){
//			// mid range
//			//System.out.println("mid distance: " + distance);
//			index = roulletWheel(midActions);
//		}
		else if(distance < 180){
			// close combat
			System.out.println("near distance: " + distance);
			index = roulletWheel(nearActions);
		}
		
//		Action[] actionInRange;
//		if(myCharacter.getState() == State.AIR && commandCenter.getDistanceY() < 300)
//			actionInRange = filterActionRange(airActions);
//		else
//			actionInRange = filterActionRange(attackActions);
//		
//		index = roulletWheel(actionInRange);
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
			//int randomNumber = new Random().nextInt(56);
			return Action.FOR_JUMP.ordinal(); //if no action remains, then jump_forward
		}
		
		updateProbabilities(reducedActionSet);
		Enum<Action> enemyAction = oppCharacter.getAction();
		int index = 0;
		float summedProb = 0;
		float randomNumber = new Random().nextFloat();
		
		for (int i = 0; i < reducedActionSet.length; i++){
			summedProb += actionProbability[reducedActionSet[i].ordinal()][enemyAction.ordinal()];
			//System.out.println("random: " + randomNumber + " summedprob: " + summedProb);
			if(randomNumber < summedProb){
				index = i;
				break;
			}
		}
		return reducedActionSet[index].ordinal();
	}
	
	private Action[] filterActionRange(Action[] actionSet){
		Action[] filteredActionSet = null;
		List<Enum<Action>> actions = new ArrayList<>();
		
		int rangeExtension = 10 + 100 / (1 +  numberGames);
		
		for (int i = 0; i < actionSet.length; i++){
			//System.out.println("energy: " + myCharacter.energy + " needed: " + -myMotionData.elementAt(actionSet[i].ordinal()).attackStartAddEnergy);
			if(distance > actionMinRange[actionSet[i].ordinal()] - rangeExtension
				&& distance < actionMaxRange[actionSet[i].ordinal()] + rangeExtension 
				|| actionMaxRange[actionSet[i].ordinal()] <= 0)
			{
				actions.add(actionSet[i]);
			}
		}
		filteredActionSet = actions.toArray(new Action[0]);
		
		return filteredActionSet;
	}
	
	private void printActionValues(){
		for(int i = 0; i < Action.values().length; i++)
			for (int j = 0; j < Action.values().length; j++)
				System.out.print(actionValue[i][j] + " ");
		System.out.println(" ");
	}

}
