import gameInterface.AIInterface;
import structs.CharacterData;
import structs.FrameData;
import structs.GameData;
import structs.Key;

import commandcenter.CommandCenter;
import enumerate.Action;
import java.util.*;

public class FightingGameAI implements AIInterface {

	private Key inputKey;
	private boolean player;
	private FrameData frameData;
	private CommandCenter commandCenter;
	
	private List<Enum<Action>> pastActions;
	private Action[] groundActions;
	private Action[] airActions;
	private float[] actionValue;
	private float[] actionProbability;
	
	/** self information */
	private CharacterData myCharacter;
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
	public int initialize(GameData arg0, boolean player) 
	{
		// TODO Auto-generated method stub
		inputKey = new Key();
		this.player = player;  // own player (enemy is negated)
		frameData = new FrameData();
		commandCenter = new CommandCenter();
		
		pastActions = new ArrayList<>();
		
		groundActions = new Action[] {Action.STAND_D_DB_BA, Action.BACK_STEP, Action.FORWARD_WALK, Action.DASH,
		                Action.JUMP, Action.FOR_JUMP, Action.BACK_JUMP, Action.STAND_GUARD,
		                Action.CROUCH_GUARD, Action.THROW_A, Action.THROW_B, Action.STAND_A, Action.STAND_B,
		                Action.CROUCH_A, Action.CROUCH_B, Action.STAND_FA, Action.STAND_FB, Action.CROUCH_FA,
		                Action.CROUCH_FB, Action.STAND_D_DF_FA, Action.STAND_D_DF_FB, Action.STAND_F_D_DFA,
		                Action.STAND_F_D_DFB, Action.STAND_D_DB_BB};
		
		airActions = new Action[] {Action.AIR_GUARD, Action.AIR_A, Action.AIR_B, Action.AIR_DA, Action.AIR_DB,
		                Action.AIR_FA, Action.AIR_FB, Action.AIR_UA, Action.AIR_UB, Action.AIR_D_DF_FA,
		                Action.AIR_D_DF_FB, Action.AIR_F_D_DFA, Action.AIR_F_D_DFB, Action.AIR_D_DB_BA,
		                Action.AIR_D_DB_BB};
		
		actionValue = new float[groundActions.length + airActions.length];
		actionProbability = new float[actionValue.length];
		float prob = 1 / actionProbability.length;
		for (int i = 0; i < actionValue.length; i++){
			actionValue[i] = 1;
			actionProbability[i] = prob;
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
				if(detectHPdiff(myCharacter, oppCharacter)){
					System.out.println("hp diff");
					currentScore = calcScore(myCharacter, oppCharacter);
					System.out.println(currentScore);
					updateActionValues((float) (currentScore * 0.01));
				}
				
				
				int energy = myCharacter.getEnergy();
				
				
				String chosenAction = choseAction();
				//System.out.println(chosenAction);
				
				
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
		// TODO: limit actionValues from 0 to 1
		float discountValue = 0.5f;
		float discountFaktor = 1.0f;
		
		if(!pastActions.isEmpty()){
			for (int i = 0; i < pastActions.size(); i++){
				Enum<Action> index = pastActions.get(i);
				//System.out.println(index.ordinal());
				actionValue[index.ordinal()] += score * discountFaktor; 
				if(actionValue[index.ordinal()] < 0)
					actionValue[index.ordinal()] = 0.0001f;
				discountFaktor *= discountValue;
			}
		}
		
		pastActions.clear();
		System.out.println(Arrays.toString(actionValue));
		updateProbabilities();
	}
	
	private void updateProbabilities()
	{
		float summedScore = 0;
		
		for(int i = 0; i < actionValue.length; i++){
			summedScore += actionValue[i];
		}
		
		if(summedScore <= 0){
			summedScore = 1;
		}
		
		for(int i = 0; i < actionProbability.length; i++){
			actionProbability[i] = actionValue[i] / summedScore;
		}
		
		System.out.println(Arrays.toString(actionProbability));
		
	}
	
	private String choseAction()
	{
		// select action based on probability
		int index = roulletWheel();
		
		// list next action for scoring
		pastActions.add(Action.values()[index]);
		return Action.values()[index].name();
	}
	
	private int roulletWheel()
	{
		int index = 0;
		float summedProb = 0;
		float randomNumber = new Random().nextFloat();
		
		//System.out.println("rand: " + randomNumber);
		
		for (int i = 0; i < actionProbability.length; i++){
			summedProb += actionProbability[i];
			if(randomNumber < summedProb){
				//System.out.println(summedProb + " " + i);
				index = i;
				break;
			}
		}
		
		return index;
	}

}
