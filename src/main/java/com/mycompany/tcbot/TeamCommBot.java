package com.mycompany.tcbot;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import cz.cuni.amis.pathfinding.alg.astar.AStarResult;
import cz.cuni.amis.pathfinding.map.IPFMapView;
import cz.cuni.amis.pogamut.base.agent.navigation.IPathFuture;
import cz.cuni.amis.pogamut.base.agent.navigation.impl.PrecomputedPathFuture;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.floydwarshall.FloydWarshallMap;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType.Category;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UT2004ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ConfigChange;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GameInfo;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.InitedMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.NavPoint;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.NavPointNeighbourLink;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Player;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerMessage;
import cz.cuni.amis.pogamut.ut2004.teamcomm.bot.UT2004BotTCController;
import cz.cuni.amis.pogamut.ut2004.teamcomm.server.UT2004TCServer;
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.utils.exception.PogamutException;

/**
 * Example of the bot that is communicating via {@link UT2004TCServer} using Apache Mina under the belt.
 * <p><p>
 * Meant to be executed on some CTF map (e.g. CTF-1on1-Joust).
 * <p><p>
 * See {@link TCHello} for example how to create custom messages.
 * <p><p>
 * See {@link #logic()} for examples how to communicate
 * <p><p>
 * Read through the whole code to discover how you can use {@link EventListener} annotations to receive messages. 
 *
 * @author Jakub Gemrot aka Jimmy
 */
@AgentScoped
public class TeamCommBot extends UT2004BotTCController<UT2004Bot>
{
	// GAME SETTINGS
	// Current year
	private static String year;
	// Red = 0 ; Blue = 1
	private static int team;
	// Skill of bots
	private static int skill;
	// Count of bots in one team
	private static int teamBotsCount;
	// Network address
	private static String address;
	// Port
	private static final int PORT = 3000;
	
	// Global variables
	private static final String STEALER = "Stealer";
	private static final String DEFENDER = "Defender";
	private static final String PATHNODE = "PathNode";
	
	// GV actions
	private static final String COMBAT = " ... combat!";
	private static final String RUN_FOR_FLAG = " ... run for flag!";
	private static final String RUN_HOME_WITH_FLAG = " ... run home with enemy flag!";
	private static final String GUARDING = " ... guarding home base!";
	private static final String PICKUP_HEALT = " ... pick up health!";
	private static final String PICKUP_WEAPON = " ... pick up weapon!";
	private static final String PICKUP_AMMO = " ... pick up ammo!";
	private static final String RUN_BEHIND_FLAG_INVISIBLE = " ... run behind flag! (INVISIBLE)";
	private static final String RUN_BEHIND_FLAG_VISIBLE = " ... run behind flag! (VISIBLE)";
	
	private static final int MAX_CYCLES_TO_AUTOKILL = 50;
	private int tryingCounter = 0;
	
	// Default defenders locations
	private int [] defaultPositions;
	private NavPoint defenderPosition;
	
	// NavPoints for CITADEL
	private static final String MAP_NAME_CITADEL = "CTF-Citadel";
	// p1, p2, tower
	int [] defaultRedPositionCitadel = {107, 18, 14};
	// p1, p2, tower
	int [] defaultBluePositionCitadel = {108, 35, 36};
	
	// NavPoints for MAUL
	private static final String MAP_NAME_MAUL = "CTF-Maul";
	// p1, p2, tower
	int [] defaultRedPositionMaul = {99, 116, 46};
	// p1, p2, tower
	int [] defaultBluePositionMaul = {17, 139, 54};
	
	// NavPoints for BP2-CONCENTRATE
	private static final String MAP_NAME_BP2 = "CTF-BP2-Concentrate";
	// p1, p2, tower
	int [] defaultRedPositionBP2 = {77, 78, 50};
	// p1, p2, tower
	int [] defaultBluePositionBP2 = {35, 23, 41};
	
    // Target navigation point of bot way
    private ILocated targetNavPoint = null;
    
	// MSGs data for STEALERS
	private Location stolenEnemyFlagLocationSend = null;
	private Location stolenEnemyFlagLocationRecv = null;
	
	// MSGs data for DEFENDERS
	private Location stolenOurFlagLocationSend = null;
	private Location stolenOurFlagLocationRecv = null;
	
	// MSGs data for both
	private Location nearestEnemyBotLocationSend = null;
	private Location nearestEnemyBotLocationRecv = null;
	
	// TMP msg for showing communication between Bots
	private TreeMap<Integer, TCRoleDefender> defenders;
	private TreeMap<Integer, TCRoleStealer> stealers;

	// Weapons ranges
	private static final int RANGE_LOW = 50;
    private static final int RANGE_MIDDLE = 500;
    private static final int RANGE_HIGH = 1000;
    private static final int RANGE_SUPERHIGH = 4000;
    private static final int DISTANCE_PICK_UP_ITEM_FOR_STEALER = 500;
    private static final int DISTANCE_PICK_UP_ITEM_FOR_DEFENDER = 3000;
    
	private static int number = 0;
	private int botNumber;
	private boolean stealer = false;
	private boolean usingCoverPath = false;
	private boolean navigatingCoverPath = false;
	private boolean recvdOurFlagPositionTrully = false;
	private boolean recvdEnemyFlagPositionTrully = false;
	
    @Override
    public Initialize getInitializeCommand() {
    	botNumber = ++number;
    	
    	Initialize init = new Initialize();
    	init.setDesiredSkill(skill);
    	init.setTeam(team);
    	init.setName(team + "_" + getBotName() + "_" + botNumber);
        
    	return init;
    }

    @Override
    public void mapInfoObtained()
    {
    	// Remove some edges
        removeEdgesAccordingMaps(game.getMapName());
    }
    
    @Override
    public void botInitialized(GameInfo gameInfo, ConfigChange config, InitedMessage init) {
    	bot.getLogger().getCategory("Yylex").setLevel(Level.OFF);
        tcClient.getLog().setLevel(Level.ALL);    	
        
        // Preferencies for guns
        // true - primary mode
        // false - secondary mode
        weaponPrefs.addGeneralPref(UT2004ItemType.LIGHTNING_GUN, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.SHOCK_RIFLE, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.MINIGUN, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.MINIGUN, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.LINK_GUN, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.LINK_GUN, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.FLAK_CANNON, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.FLAK_CANNON, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.ASSAULT_RIFLE, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.ROCKET_LAUNCHER, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.SHIELD_GUN, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.BIO_RIFLE, true);
        
        weaponPrefs.newPrefsRange(RANGE_LOW)
        .add(UT2004ItemType.FLAK_CANNON, true)
        .add(UT2004ItemType.BIO_RIFLE, true)
        .add(UT2004ItemType.LINK_GUN, false)
        .add(UT2004ItemType.SHIELD_GUN, true)
        .add(UT2004ItemType.ASSAULT_RIFLE, true);
			
		weaponPrefs.newPrefsRange(RANGE_MIDDLE)
		.add(UT2004ItemType.FLAK_CANNON, true)
		.add(UT2004ItemType.LINK_GUN, true)
        .add(UT2004ItemType.MINIGUN, false)
        .add(UT2004ItemType.BIO_RIFLE, true)
        .add(UT2004ItemType.ASSAULT_RIFLE, true);
				
		weaponPrefs.newPrefsRange(RANGE_HIGH)				
		.add(UT2004ItemType.SHOCK_RIFLE, true)
		.add(UT2004ItemType.MINIGUN, false)
		.add(UT2004ItemType.FLAK_CANNON, false)
        .add(UT2004ItemType.ROCKET_LAUNCHER, true);
				
		weaponPrefs.newPrefsRange(RANGE_SUPERHIGH)
        .add(UT2004ItemType.LIGHTNING_GUN, true)
        .add(UT2004ItemType.SHOCK_RIFLE, true);  	  
    }
    
    // ======================
    // CLIENT DEFINED MESSAGE
    // ======================
    
    @EventListener(eventClass=TCRoleStealer.class)
	public void hello(TCRoleStealer hello)
    {
    	if (stealers != null)
    	{
    		stealers.put(hello.getID(), hello);
    	}
	}
    
    @EventListener(eventClass=TCRoleDefender.class)
	public void hello(TCRoleDefender hello)
    {
    	if (defenders != null)
    	{
    		defenders.put(hello.getID(), hello);
    	}
	}
    
    // =====
    // LOGIC
    // =====
    
    int msgNum = 0;
    int myChannelId = -1;
    
    @Override
    public void beforeFirstLogic()
    {
    	if (game.getMapName().equals(MAP_NAME_CITADEL))
    	{
    		if (team == 0)
    		{
    			defaultPositions = defaultRedPositionCitadel;
    		}
    		else
    		{
    			defaultPositions = defaultBluePositionCitadel;
    		}
    	}
    	else if (game.getMapName().equals(MAP_NAME_BP2))
    	{
    		if (team == 0)
    		{
    			defaultPositions = defaultRedPositionBP2;
    		}
    		else
    		{
    			defaultPositions = defaultBluePositionBP2;
    		}
    	}
    	else if (game.getMapName().equals(MAP_NAME_MAUL))
    	{
    		if (team == 0)
    		{
    			defaultPositions = defaultRedPositionMaul;
    		}
    		else
    		{
    			defaultPositions = defaultBluePositionMaul;
    		}
    	}
    	else
    	{
    		log.info("Bots are not configured for map " + game.getMapName() + "!");
    	}
    	
    	defenders = new TreeMap<Integer, TCRoleDefender>();
    	stealers = new TreeMap<Integer, TCRoleStealer>();
    	
    	if (!stealer)
    	{
    		defenderPosition = navPoints.getNavPoint(PATHNODE + defaultPositions[botNumber - 1]);
    	}
    }
    
    @Override
    public void logic() throws PogamutException
    {	   
    	
    	// TODO - orientation point
    	connectionToTC();
    	// Get information from communication stream
    	getStolenOurFlagPosition();
    	getStolenEnemyFlagPosition();
    	// Send data to stream
    	sendDataViaTC();
    	// Kill passive bot
    	killPassiveBot();
    	
    	// ********** CODE FOR STEALER
    	if (stealer)
    	{
    		getNearestEnemyLocationForStealers();
    		stealerBehaviour();
    	}
    	// ********** CODE FOR DEFENDER
    	else
    	{
    		getNearestEnemyLocationForDefenders();
    		defenderBehaviour();
    	}
 
    	// ********** CODE FOR NAVIGATION
    	if (usingCoverPath && navigation.isNavigating())
    	{
    		return;
    	}

    	navigate(targetNavPoint);    		
    	notMoveTurnAround();
    }
    
    private void killPassiveBot()
    {
    	if (navigation.isTryingToGetBackToNav())
    	{
    		log.info("Trying to go back to navigation mesh " + getBotName());
    		tryingCounter++;
    		if (tryingCounter > MAX_CYCLES_TO_AUTOKILL)
    		{
    			log.info(getBotName() + " will be killed!");
    			tryingCounter = 0;
    			getBot().kill();    			
    		}
    	}
    	else
    	{
    		tryingCounter = 0;
    	}
    }
    
    private void sendDataViaTC()
    {
    	sendMsgToStealers("MSG from: " + getName());    		
    	sendMsgToDefenders("MSG from: " + getName());   
    }
    
    private void getStolenEnemyFlagPosition()
    {
    	if (ctf.isEnemyFlagHome())
    	{
    		stolenEnemyFlagLocationRecv = null;
    		return;
    	}
    	
    	for (Map.Entry<Integer, TCRoleStealer> stealer : stealers.entrySet())
    	{
    		if (stealer.getValue().getEnemyFlagLocation() != null)
    		{
    			stolenEnemyFlagLocationRecv = stealer.getValue().getEnemyFlagLocation();
    		}
    	}
    	
    	if (info.atLocation(stolenEnemyFlagLocationRecv, 30))
    	{
    		stolenEnemyFlagLocationRecv = null;
    	}
    }
    
    private void getStolenOurFlagPosition()
    {
    	if (ctf.isOurFlagHome())
    	{
    		stolenOurFlagLocationRecv = null;
    		return;
    	}
    	
    	for (Map.Entry<Integer, TCRoleStealer> stealer : stealers.entrySet())
    	{
    		if (stealer.getValue().getOurFlagLocation() != null)
    		{
    			stolenOurFlagLocationRecv = stealer.getValue().getOurFlagLocation();
    		}
    	}
    	
    	for (Map.Entry<Integer, TCRoleDefender> defender : defenders.entrySet())
    	{
    		if (defender.getValue().getOurFlagLocation() != null)
    		{
    			stolenOurFlagLocationRecv = defender.getValue().getOurFlagLocation();
    		}
    	}

    	if (info.atLocation(stolenOurFlagLocationRecv, 30))
    	{
    		stolenOurFlagLocationRecv = null;    			
    	}
    }
    
    private void getNearestEnemyLocationForStealers()
    {
    	for (Map.Entry<Integer, TCRoleStealer> stealer : stealers.entrySet())
    	{
    		if (stealer.getValue().getNearestEnemyBotLocation() != null)
    		{
    			nearestEnemyBotLocationRecv = stealer.getValue().getNearestEnemyBotLocation();
    		}
    	}
    }
    
    private void getNearestEnemyLocationForDefenders()
    {
    	for (Map.Entry<Integer, TCRoleDefender> defender : defenders.entrySet())
    	{
    		if (defender.getValue().getNearestEnemyBotLocation() != null)
    		{
    			nearestEnemyBotLocationRecv = defender.getValue().getNearestEnemyBotLocation();
    		}
    	}
    }
    
    private boolean pickUpItemsViaDistanceTypeAndCategory(int distanceTreshold, Location locationOfRadius)
    {
    	if (pickUpItemViaUTypeAndDistance(UT2004ItemType.U_DAMAGE_PACK, distanceTreshold, locationOfRadius))
    	{
    		bot.getBotName().setInfo(" ... pick up double damage!");
    		return true;
    	}
    	
    	if (pickUpItemViaCategoryAndDistance(Category.WEAPON, distanceTreshold, locationOfRadius))
    	{
    		bot.getBotName().setInfo(PICKUP_WEAPON);
    		return true;
    	}
    	
    	if (pickUpItemViaCategoryAndDistance(Category.HEALTH, distanceTreshold, locationOfRadius))
		{
			bot.getBotName().setInfo(PICKUP_HEALT);
			return true;
		}

    	if (pickUpItemViaCategoryAndDistance(Category.AMMO, distanceTreshold, locationOfRadius))
    	{
    		bot.getBotName().setInfo(PICKUP_AMMO);
    		return true;
    	}
    	
    	if (pickUpItemViaCategoryAndDistance(Category.ARMOR, distanceTreshold, locationOfRadius))
    	{
    		bot.getBotName().setInfo(" ... pick up shield!");
    		return true;
    	}
    	
		return false;
    }
    
    private boolean pickUpItemViaUTypeAndDistance(UT2004ItemType type, int distanceTreshold, Location locationOfRadius)
    {
    	Item item = items.getNearestSpawnedItem(type);
    	
    	if (item != null
    			&& items.isPickable(item)
    			&& items.isPickupSpawned(item)
    			&& fwMap.getDistance(navPoints.getNearestNavPoint(locationOfRadius), item.getNavPoint())
				< distanceTreshold)
    	{
    		targetNavPoint = item.getLocation();
    		return true;
    	}
    	
    	return false;
    }
    
    private boolean pickUpItemViaCategoryAndDistance(Category category, int distanceTreshold, Location locationOfRadius)
    {
    	Item item = items.getNearestSpawnedItem(category);
    	
		if (item != null
				&& items.isPickable(item)
    			&& items.isPickupSpawned(item)
				&& fwMap.getDistance(navPoints.getNearestNavPoint(locationOfRadius), item.getNavPoint())
					< distanceTreshold)
		{
			targetNavPoint = item.getLocation();
			return true;
		}
		
		return false;
    }
    
    private void notMoveTurnAround()
    {
    	if (!navigation.isNavigating()
    			&& !players.canSeeEnemies()
    			&& !info.isShooting())
    	{
    		move.turnHorizontal(90);
    	}
    }
    
    private void stealerBehaviour()
    {
    	// STEALER *** COMBAT
    	if (combatStealer())
    	{
    		bot.getBotName().setInfo(COMBAT);
    	}
    	
		// STEALER *** RUN FOR FLAG
    	if (ctf.isEnemyFlagHome())
    	{
    		if(runForFlag())
    		{
    			bot.getBotName().setInfo(RUN_FOR_FLAG);
    		}
    		recvdEnemyFlagPositionTrully = true;
    	}
    	
    	if (!ctf.isEnemyFlagHome())
    	{
    		// STEALER *** HAVE ENEMY FLAG, RUN HOME
    		if (ctf.isBotCarryingEnemyFlag())
    		{
    			behaviourWithEnemyFlag();
    		}
    		
    		// STEALER *** HAVE NOT FLAG, OTHER BOT HAS ENEMY FLAG
    		if (!ctf.isBotCarryingEnemyFlag())
    		{
    			behaviourWithoutEnemyFlag();
    		}
    	}
    }
    
    private void behaviourWithoutEnemyFlag()
    {
    	stolenOurFlagLocationSend = null;
    	stolenEnemyFlagLocationSend = null;
    	getStolenEnemyFlagPosition();
    	
    	if (ctf.isOurFlagHome())
    	{
    		if (ctf.getEnemyFlag().isVisible())
    		{
    			bot.getBotName().setInfo(RUN_BEHIND_FLAG_VISIBLE);
    			stolenEnemyFlagLocationSend = ctf.getEnemyFlag().getLocation();
    			targetNavPoint = ctf.getEnemyFlag().getLocation();
    			sendDataViaTC();
    		}
    		else
    		{
    			if (stolenEnemyFlagLocationRecv != null && recvdEnemyFlagPositionTrully)
    			{
    				bot.getBotName().setInfo(RUN_BEHIND_FLAG_INVISIBLE);
    				targetNavPoint = navPoints.getNearestNavPoint(stolenEnemyFlagLocationRecv);
    				
    				if (info.atLocation(targetNavPoint, 30))
                    {
                        log.info("Old position of enemy flag!");
                        recvdEnemyFlagPositionTrully = false;
                    }
                    else
                    {
                        log.info("Running on hidden our flag position!");
                    }
    			}
    			else
    			{
    				bot.getBotName().setInfo(" ... return to our base!");
    				targetNavPoint = ctf.getEnemyBase().getLocation();
    			}
    		}
    	}
    	else
    	{
    		if (ctf.getOurFlag().isVisible())
    		{
    			bot.getBotName().setInfo(" ... bot see our stolen FALG!");
    			stolenOurFlagLocationSend = ctf.getOurFlag().getLocation();
    			targetNavPoint = ctf.getOurFlag().getLocation();
    			sendDataViaTC();
    		}
    		else
    		{
    			if (stolenOurFlagLocationRecv != null)
        		{
    				bot.getBotName().setInfo(" ... run for our hidden flag!");
    				targetNavPoint = navPoints.getNearestNavPoint(stolenOurFlagLocationRecv);

                    if (info.atLocation(targetNavPoint, 30))
                    {
                        log.info("Old position of our flag!");
                        recvdOurFlagPositionTrully = false;
                    }
                    else
                    {
                        log.info("Running on hidden our flag position!");
                    }
        		}
        		else
        		{
        			bot.getBotName().setInfo(" ... run to enemy base for our flag!");
        			targetNavPoint = ctf.getEnemyBase();
        		}
    		}
    	}
    }
    
    private boolean behaviourWithEnemyFlag()
    {
    	returnHome();
    	return true;
    }
    
    private void defenderBehaviour()
    {
    	// DEFENDER *** COMBAT
    	if (combatDefender())
		{
			bot.getBotName().setInfo(COMBAT);
		}

    	// DEFENDER *** GO FOR ENEMY'S FLAG
    	if (ctf.getEnemyFlag().isVisible())
    	{
    		targetNavPoint = ctf.getEnemyFlag().getLocation();
    		return;
    	}
    	
    	// DEFENDER *** GO HOME WITH ENEMY'S FLAG
    	if (ctf.isBotCarryingEnemyFlag())
    	{
    		targetNavPoint = ctf.getOurBase();
    		return;
    	}
		
		if (ctf.isOurFlagHome())
		{
			// DEFENDER *** GUARDING OUR FLAG (FLAG IS HOME)
			guardingOurBase();
			recvdOurFlagPositionTrully = true;
		}
		else
		{
			// DEFENDER *** SEARCHING OUR FLAG (FLAG IS NOT HOME)
			searchOurFlag();
		}
    }
    
    private void searchOurFlag()
    {
    	stolenOurFlagLocationSend = null;
    	stolenEnemyFlagLocationSend = null;
    	getStolenOurFlagPosition();
    	
    	if (ctf.getOurFlag().isVisible())
    	{
    		// info
    		bot.getBotName().setInfo(" ... run for our visible flag!");
    		// calibration
    		stolenOurFlagLocationSend = ctf.getOurFlag().getLocation();
    		// change target navigation point
    		targetNavPoint = ctf.getOurFlag().getLocation();
    		// pursuit flag
    		sendDataViaTC();
    	}
    	else
    	{
    		if (stolenOurFlagLocationRecv != null && recvdOurFlagPositionTrully)
    		{
    			bot.getBotName().setInfo(" ... run for our hidden flag!");
    			targetNavPoint = navPoints.getNearestNavPoint(stolenOurFlagLocationRecv);

    			if (info.atLocation(targetNavPoint, 30))
    			{
    				log.info("Old position of our flag!");
    				recvdOurFlagPositionTrully = false;
    			}
    			else
    			{
    				log.info("Running on hidden our flag position!");
    			}
    		}
    		else
    		{
    			if (ctf.getEnemyFlag().isVisible())
    	    	{
    	    		bot.getBotName().setInfo(" ... run for enemy visible flag!");
    	    		stolenEnemyFlagLocationSend = ctf.getEnemyFlag().getLocation();
    	    		targetNavPoint = ctf.getEnemyFlag().getLocation();
    	    		sendDataViaTC();
    	    	}
    			else
    			{
    				bot.getBotName().setInfo(" ... run to enemy base for our flag!");
        			targetNavPoint = ctf.getEnemyBase();
    			}
    		}
    	}
    }
    
    private boolean guardingOurBase()
    {
    	if (!players.canSeeEnemies())
    	{
    		Location centerOfRadius = null;

    		if (MAP_NAME_BP2.equals(game.getMapName()))
    		{
    			if (team == 0)
    			{
    				centerOfRadius = navPoints.getNavPoint("PathNode46").getLocation();    				
    			}
    			else
    			{
    				centerOfRadius = navPoints.getNavPoint("PathNode47").getLocation();    				
    			}
    		}
    		else
    		{
    			centerOfRadius = ctf.getOurBase().getLocation();
    		}
    		
    		if (pickUpItemsViaDistanceTypeAndCategory(DISTANCE_PICK_UP_ITEM_FOR_DEFENDER, centerOfRadius))
    		{
    			return false;
    		}   
    		
    		// Go on defenders positions
    		bot.getBotName().setInfo(GUARDING + " FROM DEF_POSITION");
    		targetNavPoint = defenderPosition;
    	}
    	else
    	{
    		bot.getBotName().setInfo(GUARDING + " FROM BASE");
    		targetNavPoint = ctf.getOurBase().getLocation();    		
    	}

    	return true;
		
    }
    
    private void sendMsgToStealers(String msg)
    {
    	// MSG Init
    	TCRoleStealer stealer = new TCRoleStealer(info.getId(), msg);
    	// MSG set variables
    	stealer.setEnemyFlagLocation(stolenEnemyFlagLocationSend);
    	stealer.setOurFlagLocation(stolenOurFlagLocationSend);
    	stealer.setID(botNumber);
    	stealer.setCurrentLocation(info.getLocation());
    	
    	// MSG send
    	tcClient.sendToTeam(stealer);
    }

    private void sendMsgToDefenders(String msg)
    {
    	// MSG Init
    	TCRoleDefender defender = new TCRoleDefender(info.getId(), msg);
    	// MSG set variables
    	defender.setOurFlagLocation(stolenOurFlagLocationSend);
    	defender.setID(botNumber);
    	defender.setCurrentLocation(info.getLocation());
    	defender.setNearestEnemyBotLocation(nearestEnemyBotLocationSend);
    	
    	// MSG send
    	tcClient.sendToTeam(defender);
    }
    
    private boolean connectionToTC()
    {
    	if (!tcClient.isConnected())
    	{
    		msgNum = 0;
    		myChannelId = -1;
    		log.info("Connection to TeamComunicator FAILED ...");
    	
    		return false;
    	}
    	
    	if (botNumber != 1)
    	{
    		return false;
    	}
    	
    	if (tcClient.getConnectedAllBots().size() != teamBotsCount)
    	{
    		// wait till all bots get connected...
    		return false;
    	}
    	
    	return true;
    }
    
    private boolean combatStealer()
    {
    	// BOT is shooting
    	if (shooting())
    	{
    		return true;
    	}
    	
    	// BOT isn't shooting
    	if (senses.isShot())
		{
			navigation.setFocus(navigation.getNearestNavPoint(info.getLocation()));
		}
    	
    	return false;
    }
    
	private boolean shooting()
	{
		// BOT can see enemies
		if (players.canSeeEnemies())
    	{
			nearestEnemyBotLocationSend = players.getNearestVisibleEnemy().getLocation();
			navigation.setFocus(nearestEnemyBotLocationSend);
			shoot.shoot(weaponPrefs, nearestEnemyBotLocationSend);
			sendDataViaTC();
    	}
		else
		{
			nearestEnemyBotLocationSend = null;
			
			if (info.isShooting())
			{
				shoot.stopShooting();    			
			}
			
			if (nearestEnemyBotLocationRecv != null)
			{
				navigation.setFocus(navPoints.getNearestNavPoint(nearestEnemyBotLocationRecv));
			}
			else
			{
				navigation.setFocus(null);
			}
		}
		
    	return false;
	}

    private boolean runForFlag()
    {
    	if (!ctf.isOurFlagHome() && ctf.getOurFlag().isVisible())
    	{
    		targetNavPoint = ctf.getOurFlag().getLocation();
    		navigatingCoverPath = false;
    		return true;
    	}
    	
    	if (ctf.getEnemyFlag().isVisible())
    	{
    		targetNavPoint = ctf.getEnemyFlag().getLocation();
    		navigatingCoverPath = false;
    		return true;
    	}
    	
    	// Navigation to enemy base for FLAG
    	targetNavPoint = ctf.getEnemyBase();
    	
    	if (game.getMapName().equals(MAP_NAME_BP2))
    	{
    		if (pickUpItemsViaDistanceTypeAndCategory(DISTANCE_PICK_UP_ITEM_FOR_STEALER, info.getLocation()))
			{
				return false;
			}
    	}
    	else
    	{
    		if(players.canSeeEnemies())
    		{
    			if (!navigatingCoverPath)
    			{
    				navigateCoverPath(ctf.getEnemyBase());
    				navigatingCoverPath = true;
    			}
    		}
    		else
    		{
    			navigatingCoverPath = false;
    		}
    		
    		if (!navigatingCoverPath)
    		{
    			if (pickUpItemsViaDistanceTypeAndCategory(DISTANCE_PICK_UP_ITEM_FOR_STEALER, info.getLocation()))
    			{
    				return false;
    			}
    		}
    	}

    	return true;
    }
    
    private boolean returnHome()
    {
    	bot.getBotName().setInfo(RUN_HOME_WITH_FLAG);
    	targetNavPoint = ctf.getOurBase();
    	
    	if (!game.getMapName().equals(MAP_NAME_BP2))
    	{
    		if(players.canSeeEnemies())
    		{
    			if (!navigatingCoverPath)
    			{
    				navigateCoverPath(ctf.getOurBase());
    				navigatingCoverPath = true;
    			}
    		}
    		else
    		{
    			navigatingCoverPath = false;
    		}
    		
    		if (!navigatingCoverPath)
    		{
    			if (pickUpItemsViaDistanceTypeAndCategory(DISTANCE_PICK_UP_ITEM_FOR_STEALER, info.getLocation()))
    			{
    				return false;
    			}
    		}
    	}
    	
    	
    	stolenEnemyFlagLocationSend = info.getLocation();
    	sendDataViaTC();
    	
    	return true;
    }
    
    private void navigate(ILocated location)
    {
    	usingCoverPath = false;
		navigation.navigate(location); 
    }

    private boolean combatDefender()
    {
    	// BOT is shooting
    	if (shooting())
    	{
    		return true;
    	}
    	
    	// BOT isn't shooting
    	if (!info.isShooting() && senses.isShot())
		{
			navigation.setFocus(navigation.getNearestNavPoint(info.getLocation()));
		}
    	
    	return false;
    }

    /**
     * Called each time our bot die. Good for reseting all bot state dependent
     * variables.
     *
     * @param event
     */
    @Override
    public void botKilled(BotKilled event) {
        navigation.stopNavigation();
    }

    // ====
    // MAIN
    // ====
    
    public static UT2004TCServer tcServer;
    
    /** 
     * Do not use for production - this method will be typically unavailable in the most scenarios as it is "hack"
     * and used here for debugging only.
     * @param id
     * @return
     */
    public static PlayerMessage getPlayer(UnrealId id) {
    	return tcServer.getPlayer(id);
    }
    
    /** 
     * Do not use for production - this method will be typically unavailable in the most scenarios as it is "hack"
     * and used here for debugging only.
     * @param id
     * @return
     */
    public static String getPlayerName(UnrealId id) {
    	PlayerMessage msg = getPlayer(id);
    	if (msg == null) return id.getStringId();
    	return getPlayer(id).getName();
    }
    
    private static void initialize(String [] args) throws Exception
    {
    	year = args[0];
    	team = Integer.parseInt(args[1]);
    	skill = Integer.parseInt(args[2]);
    	teamBotsCount = Integer.parseInt(args[3]);
    	address = args[4];
    }
    
    private String getBotName()
    {
    	String tmp = "";
    	if (teamBotsCount < 5)
    	{
    		if (botNumber < 3)
    		{
    			stealer = false;
    			tmp = DEFENDER;
    		}
    		else
    		{
    			stealer = true;
    			tmp = STEALER;
    		}
    	}
    	else if (teamBotsCount < 7)
    	{
    		if (botNumber < 4)
    		{
    			stealer = false;
    			tmp = DEFENDER;
    		}
    		else
    		{
    			stealer = true;
    			tmp = STEALER;
    		}
    	}
    	else
    	{
    		if (botNumber < 5)
    		{
    			stealer = false;
    			tmp = DEFENDER;
    		}
    		else
    		{
    			stealer = true;
    			tmp = STEALER;
    		}
    	}
    	
    	return tmp;
    }
    
    private void removeEdgesAccordingMaps(String mapName)
    {
    	if (MAP_NAME_BP2.equals(mapName))
        {
    		// ***** RED
    		// middle
    		navBuilder.removeEdge("AssaultPath12", "PathNode74");
    		navBuilder.removeEdge("AssaultPath12", "PathNode81");
    		// right
    		navBuilder.removeEdge("JumpSpot2", "PathNode74");
    		// left
    		navBuilder.removeEdge("InventorySpot59", "PathNode81");
    		// others
    		navBuilder.removeEdge("JumpSpot13", "PathNode75");
    		
    		// *** BLUE
    		// middle
    		navBuilder.removeEdge("AssaultPath5", "PathNode44");
    		navBuilder.removeEdge("AssaultPath5", "PathNode0");
    		// right
    		navBuilder.removeEdge("InventorySpot55", "PathNode44");
    		// left
    		navBuilder.removeEdge("JumpSpot3", "PathNode0");
        }
    	if (MAP_NAME_CITADEL.equals(mapName))
    	{
    		// ***** RED
    		navBuilder.removeEdge("PathNode26", "PathNode23");
    		navBuilder.removeEdge("PathNode38", "PathNode23");
    		// jump to flag
    		navBuilder.removeEdge("PathNode92", "JumpSpot12");
    		navBuilder.removeEdge("PathNode100", "JumpSpot12");

    		// ***** BLUE
    		// jump to flag
    		navBuilder.removeEdge("PathNode101", "JumpSpot8");
    		navBuilder.removeEdge("PathNode102", "JumpSpot8");
    	}
    	if (MAP_NAME_MAUL.equals(mapName))
    	{
    		// ***** RED
    		navBuilder.removeEdge("PathNode67", "JumpSpot18");
    		navBuilder.removeEdge("PathNode66", "JumpSpot18");
    		navBuilder.removeEdge("PathNode95", "JumpSpot3");  // could not find 3
    		navBuilder.removeEdge("PathNode66", "JumpSpot2");
    		navBuilder.removeEdge("PathNode96", "jumpspot9");  // could not find 9
    		navBuilder.removeEdge("PathNode93", "JumpSpot0");
    		
    		// ***** BLUE
    		navBuilder.removeEdge("PathNode6", "JumpSpot20");
    		navBuilder.removeEdge("PathNode48", "JumpSpot7");  // could not find 7
    		navBuilder.removeEdge("PathNode12", "JumpSpot6"); // could not find 6
    		navBuilder.removeEdge("PathNode143", "JumpSpot8"); // could not find 8
    		navBuilder.removeEdge("PathNode6", "JumpSpot4");
    	}
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void navigateCoverPath(NavPoint runningTo)
    {
    	PrecomputedPathFuture<NavPoint> path = generateCoverPath(runningTo);
    	if (path == null) {
    		log.info("Could not generate COVER PATH");
    		navigate(runningTo);    		
    		return;
    	}
    	
    	log.info("RUNNING ALONG COVER PATH");
    	
    	usingCoverPath = true;
    	navigation.navigate((IPathFuture)path);
    	
    	drawPath(path);
    }
    
    public void drawPath(PrecomputedPathFuture<NavPoint> path)
    {
        draw.clearAll();
        
        List<NavPoint> pathPoints = path.get();
        
        for(int i = 1; i < pathPoints.size(); i++)
        {
            draw.drawLine(pathPoints.get(i - 1), pathPoints.get(i));
        }
    }
    
    private PrecomputedPathFuture<NavPoint> generateCoverPath(NavPoint runningTo)
    {
    	NavPoint startNav = info.getNearestNavPoint();
    	NavPoint targetNav = runningTo;
    	
    	AStarResult<NavPoint> result = aStar.findPath(startNav, targetNav, new CoverMapView());
    	PrecomputedPathFuture<NavPoint> pathFuture = new PrecomputedPathFuture<NavPoint>(startNav, targetNav, result.getPath());
    	
    	return pathFuture;
    }
    
    private class CoverMapView implements IPFMapView<NavPoint>
    {
    	@Override
		public Collection<NavPoint> getExtraNeighbors(NavPoint node, Collection<NavPoint> mapNeighbors) {
			return null;
		}

		@Override
		public int getNodeExtraCost(NavPoint node, int mapCost) {
            int penalty = 0;
            
            for(Player player : players.getVisibleEnemies().values())
            {
                if(visibility.isVisible(node, player.getLocation()))
                {
                    penalty += 500;
                }
            }
            
            return penalty;
		}

		@Override
		public int getArcExtraCost(NavPoint nodeFrom, NavPoint nodeTo, int mapCost) {
			return 0;
		}

		@Override
		public boolean isNodeOpened(NavPoint node) {
			// ALL NODES ARE OPENED
			return true;
		}

		@Override
		public boolean isArcOpened(NavPoint nodeFrom, NavPoint nodeTo) {
			// ALL ARCS ARE OPENED
            NavPointNeighbourLink link = nodeFrom.getOutgoingEdges().get(nodeTo.getId());
            
            if( (link.getFlags() & FloydWarshallMap.BAD_EDGE_FLAG) > 0)
            {
                return false;
            }
                        
			return true;
		}
    	
    }
    
    public static void main(String args[]) throws PogamutException, Exception
    {
    	initialize(args);
    	tcServer = UT2004TCServer.startTCServer();
    	
    	// Start bots
    	new UT2004BotRunner(TeamCommBot.class, "TCBot", address, PORT).setLogLevel(Level.WARNING).startAgents(teamBotsCount);
    }
}
