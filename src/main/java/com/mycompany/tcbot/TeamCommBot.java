package com.mycompany.tcbot;

import java.awt.Color;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;

import org.junit.experimental.categories.Categories;

import cz.cuni.amis.pathfinding.alg.astar.AStarResult;
import cz.cuni.amis.pathfinding.map.IPFMapView;
import cz.cuni.amis.pogamut.base.agent.navigation.IPathFuture;
import cz.cuni.amis.pogamut.base.agent.navigation.impl.PrecomputedPathFuture;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base.utils.math.DistanceUtils;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.navigation.floydwarshall.FloydWarshallMap;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
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
import cz.cuni.amis.utils.Heatup;
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
	private static final String PICKUP_WEAPONS = " ... pick-up weapons!";
	private static final String GET_SHOT_CANT_SEE_ENEMY =  " ... get sho and can't see enemy!";
	private static final String ENEMY_STEALERS_SEE = " ... see stolen flag!";
	private static final String ENEMY_STEALERS_CANNOT_SEE = " ... some enemy stolen flag!";
	private static final String PICKUP_HEALT = " ... pick up health!";
	private static final String PICKUP_WEAPON = " ... pick up weapon!";
	private static final String PICKUP_AMMO = " ... pick up ammo!";
	
	// Default defenders locations
	private int [] defaultPositions;
	private NavPoint defenderPosition;
	
	// NavPoints for CITADEL
	private static final String MAP_NAME_CITADEL = "CTF-Citadel";
	// p1, p2, tower
	int [] defaultRedPositionCitadel = {11, 75, 14};
	// p1, p2, tower
	int [] defaultBluePositionCitadel = {30, 41, 36};
	
	// NavPoints for MAUL
	private static final String MAP_NAME_MAUL = "CTF-Maul";
	// p1, p2, tower
	int [] defaultRedPositionMaul = {101, 117, 46};
	// p1, p2, tower
	int [] defaultBluePositionMaul = {17, 138, 54};
	
	// NavPoints for BP2-CONCENTRATE
	private static final String MAP_NAME_BP2 = "CTF-BP2-Concentrate";
	// p1, p2, tower
	int [] defaultRedPositionBP2 = {77, 78, 50};
	// p1, p2, tower
	int [] defaultBluePositionBP2 = {35, 23, 41};
	
    // Target navigation point of bot way
    private ILocated targetNavPoint = null;
    
	// MSGs data for STEALERS
	private ILocated stealedEnemyFlagLocationSend = null;
	private ILocated stealedEnemyFlagLocationRecv = null;
	
	// MSGs data for DEFENDERS
	private ILocated stealedOurFlagLocationSend = null;
	private ILocated stealedOurFlagLocationRecv = null;
	
	// TMP msg for showing communication between Bots
	private TreeMap<Integer, TCRoleDefender> defenders;
	private TreeMap<Integer, TCRoleStealer> stealers;

	// Weapons ranges
	private static final int RANGE_LOW = 50;
    private static final int RANGE_MIDDLE = 500;
    private static final int RANGE_HIGH = 1000;
    private static final int RANGE_SUPERHIGH = 4000;
    private static final int DISTANCE_PICK_UP_ITEM_FOR_FLAGSTEALER = 600;
    private static final int DISTANCE_PICK_UP_ITEM_FOR_DEFENDER = 1500;
	
	private static int number = 0;
	private int botNumber;
	private boolean stealer = false;
	
	private ILocated ourFlagLocationOrigin;
	private ILocated enemyFlagLocationOrigin;
	
	private int mainStealer;
	private int mainDefender;
	
	private Heatup pursueEnemy = new Heatup(3000);
	private UnrealId visibleSpawnedItemSend;
	
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
    public void botInitialized(GameInfo gameInfo, ConfigChange config, InitedMessage init) {
    	bot.getLogger().getCategory("Yylex").setLevel(Level.OFF);
        tcClient.getLog().setLevel(Level.ALL);    	
        
        // Preferencies for guns
        // true - primary mode
        // false - secondary mode
        weaponPrefs.addGeneralPref(UT2004ItemType.LIGHTNING_GUN, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.SHOCK_RIFLE, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.MINIGUN, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.LINK_GUN, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.FLAK_CANNON, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.ASSAULT_RIFLE, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.ROCKET_LAUNCHER, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.SHIELD_GUN, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.BIO_RIFLE, true);
        
        weaponPrefs.newPrefsRange(RANGE_LOW)
        .add(UT2004ItemType.SHIELD_GUN, true);
		// Only one weapon is added to this close combat range and it is SHIELD GUN		
			
		// Second range class is from 80 to 1000 ut units (its always from the previous class to the maximum
		// distance of actual class
		weaponPrefs.newPrefsRange(RANGE_MIDDLE)
		        .add(UT2004ItemType.FLAK_CANNON, true)
		        .add(UT2004ItemType.MINIGUN, true)
		        .add(UT2004ItemType.LINK_GUN, false)
		        .add(UT2004ItemType.ASSAULT_RIFLE, true);        
		// More weapons are in this class with FLAK CANNON having the top priority		
				
		// Third range class is from 1000 to 4000 ut units - that's quite far actually
		weaponPrefs.newPrefsRange(RANGE_HIGH)
		        .add(UT2004ItemType.SHOCK_RIFLE, true)
		        .add(UT2004ItemType.MINIGUN, false);
		// Two weapons here with SHOCK RIFLE being the top
				
		// The last range class is from 4000 to 100000 ut units. In practise 100000 is
		// the same as infinity as there is no map in UT that big
		weaponPrefs.newPrefsRange(RANGE_SUPERHIGH)
		        .add(UT2004ItemType.LIGHTNING_GUN, true)
		        .add(UT2004ItemType.SHOCK_RIFLE, true);  	  
		// Only two weapons here, both good at sniping
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
    	
    	if (hello.getEnemyFlagLocation() != null)
    	{
    		stealedEnemyFlagLocationRecv = hello.getEnemyFlagLocation();
    	}
	}
    
    @EventListener(eventClass=TCRoleDefender.class)
	public void hello(TCRoleDefender hello)
    {
    	if (defenders != null)
    	{
    		defenders.put(hello.getID(), hello);
    	}
    	
    	if (hello.getOurFlagLocation() != null)
    	{
    		stealedOurFlagLocationRecv = hello.getOurFlagLocation();
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
    	
    	ourFlagLocationOrigin = ctf.getOurFlag().getLocation();
    	enemyFlagLocationOrigin = ctf.getEnemyFlag().getLocation();
    }
    
    @Override
    public void logic() throws PogamutException
    {	    
    	// TODO - orientation point
    	connectionToTC();
    	
    	// ********** CODE FOR STEALER
    	if (stealer)
    	{
    		sendMsgToStealers("MSG from: " + getName());    		
    		mainStealer = stealers.size() > 0 ? stealers.firstKey() : -1;

    		if (stealerBehaviour())
    		{
    			return;
    		}
    		
    		// target navigation point controller
        	stealerMovingController();
    	}
    	// ********** CODE FOR DEFENDER
    	else
    	{
    		sendMsgToDefenders("MSG from: " + getName());    		
    		mainDefender = defenders.size() > 0 ? defenders.firstKey() : -1;
    		
    		if (defenderBehaviour())
    		{
    			return;
    		}
    		
    		// target navigation point controller
    		defenderMovingController();
    	}
 
    	// ********** CODE FOR NAVIGATION
    	navigate(targetNavPoint);
    	notMoveTurnAround();
    	
    	List<NavPoint> nearest = fwMap.getPath(info.getNearestNavPoint(), navigation.getNearestNavPoint(targetNavPoint));
    	draw.clearAll();
    	drawPath(nearest, Color.WHITE);
    	
    	// TODO - nastavit sber zbrani podle vzdalenosti pro defendera - momentalne to bere moc casu
    	// TODO - pridat sber munice kdyz nemas (jinak nestrili)
    	// TODO - pouzivat heatup a cooldown = prodlouzeni akce
    	// TODO - zkusit vzdy bezet naplno
    	// TODO - zkusit prenastavit coverpath
    }
    
    private boolean pickUpItemsViaDistanceAndCategory()
    {
    	if (info.getHealth() < 100)
    	{
    		if (pickUpNearestHealth())
    		{
    			bot.getBotName().setInfo(PICKUP_HEALT);
    			return true;
    		}
    	}

    	if (pickUpNearestWeapon())
    	{
    		bot.getBotName().setInfo(PICKUP_WEAPON);
    		return true;
    	}
    	
    	if (pickUpNearestAmmo())
    	{
    		bot.getBotName().setInfo(PICKUP_AMMO);
    		return true;
    	}
		
		return false;
    }
    
    private boolean pickUpNearestAmmo()
    {
    	Item item = items.getNearestVisibleItem(Category.AMMO);
    	
    	if (item != null
    			&& fwMap.getDistance(info.getNearestNavPoint(), item.getNavPoint()) < DISTANCE_PICK_UP_ITEM_FOR_DEFENDER)
    	{
    		targetNavPoint = item.getLocation();
    		return true;
    	}
    	
    	return false;
    }
    
    private boolean pickUpNearestHealth()
    {
    	// health
    	Double nearestDistance = Double.MAX_VALUE;
    	
    	for (Map.Entry<UnrealId, Item> it : items.getAllItems(Category.HEALTH).entrySet())
		{
			Double itemDistance = fwMap.getDistance(info.getNearestNavPoint(), it.getValue().getNavPoint());
			if (itemDistance < DISTANCE_PICK_UP_ITEM_FOR_DEFENDER)
			{
				if (itemDistance < nearestDistance)
				{
					nearestDistance = itemDistance;
					targetNavPoint = it.getValue().getLocation();
				}
			}
		}
		
		if (nearestDistance != Double.MAX_VALUE)
		{
			return true;
		}
		else
		{
			return false;
		}
    }
    
    private boolean pickUpNearestWeapon()
    {
    	Map<UnrealId, Item> weapons = items.getAllItems(Category.WEAPON);
    	Double nearestDistance = Double.MAX_VALUE;
    	
    	for (Map.Entry<UnrealId, Item> weapon : weapons.entrySet())
    	{
    		if (!weaponry.hasWeapon(weapon.getValue().getType()))
    		{
    			Double itemDistance = fwMap.getDistance(info.getNearestNavPoint(), weapon.getValue().getNavPoint());
    			if (itemDistance < DISTANCE_PICK_UP_ITEM_FOR_DEFENDER)
    			{
    				if (itemDistance < nearestDistance)
    				{
    					nearestDistance = itemDistance;
    					targetNavPoint = weapon.getValue().getLocation();
    				}
    			}
    		}
    	}
    	
    	if (nearestDistance != Double.MAX_VALUE)
    	{
    		return true;
    	}
    	else
    	{
    		return false;
    	}
    }
    
    private void notMoveTurnAround()
    {
    	if (!navigation.isNavigating())
    	{
    		move.turnHorizontal(90);
    	}
    }
    
    private boolean stealerBehaviour()
    {
    	// communication values calibration
    	stealedEnemyFlagLocationSend = null;
    	
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
    		else
    		{
    			bot.getBotName().setInfo(PICKUP_WEAPONS);
    		}
    	}
    	// STEALER *** HAVE FLAG, RUN HOME
    	if (!ctf.isEnemyFlagHome() && ctf.isBotCarryingEnemyFlag())
    	{
    		scoreIfIsItPossible();
    	}
    	// STEALER *** HAVE NOT FLAG, OTHER BOT HAS FLAG
    	if (!ctf.isEnemyFlagHome() && !ctf.isBotCarryingEnemyFlag())
    	{
    		if (ctf.canOurTeamScore())
    		{
    			if (stealedEnemyFlagLocationRecv != null)
    			{
    				targetNavPoint = stealedEnemyFlagLocationRecv;
    			}    			
    		}
    	}
    	
    	return false;
    }
    
    
    private void stealerMovingController()
    {
    	if (targetNavPoint == null)
    	{
    		if(ctf.isEnemyFlagHome())
    		{
    			targetNavPoint = ctf.getEnemyFlag().getLocation();
    		}
    		if (ctf.isBotCarryingEnemyFlag())
    		{
    			targetNavPoint = ctf.getOurFlag().getLocation();
    		}
    	}
    }
    
    private boolean scoreIfIsItPossible()
    {
    	returnHome();
    	stealedEnemyFlagLocationSend = info.getNearestNavPoint().getLocation();
    	
    	return true;
    }
    
    private boolean defenderBehaviour()
    {
    	if (combatDefender())
		{
			bot.getBotName().setInfo(COMBAT);
		}
		
		if (ctf.isOurFlagHome())
		{
			if(guardingOurBase())
			{
				bot.getBotName().setInfo(GUARDING);
			}
		}

		if (!ctf.isOurFlagHome())
		{
			if (ourFlagLocationOrigin != ctf.getOurFlag().getLocation())
			{
				bot.getBotName().setInfo(ENEMY_STEALERS_SEE);
				targetNavPoint = ctf.getOurFlag().getLocation();
				stealedOurFlagLocationSend = ctf.getOurFlag().getLocation();
				
				if (info.getLocation().equals(targetNavPoint))
				{
					if (stealedOurFlagLocationRecv != null)
					{
						targetNavPoint = stealedOurFlagLocationRecv;						
					}
					else
					{
						targetNavPoint = ctf.getEnemyBase();
					}
				}
			}
			else
			{
				bot.getBotName().setInfo(ENEMY_STEALERS_CANNOT_SEE);
				targetNavPoint = ctf.getEnemyBase();				
			}
		}
		
		return false;
    }
    
    private void defenderMovingController()
    {
    	if (targetNavPoint == null)
    	{
    		if (ctf.isOurFlagHome())
    		{
    			targetNavPoint = defenderPosition;
    		}
    		else
    		{
    			targetNavPoint = ctf.getEnemyBase().getLocation();
    		}
    	}
    }
    
    private boolean guardingOurBase()
    {
    	if (!players.canSeeEnemies())
    	{
    		//if (getItemViaDistanceAndUrgentHealth())
    		if (pickUpItemsViaDistanceAndCategory())
    		{
    			return false;
    		}    		
    		if (isGetShotWithoutSeenEnemy())
    		{
    			bot.getBotName().setInfo(GET_SHOT_CANT_SEE_ENEMY);
    			return false;
    		}
    		
    		// Go on defenders positions
    		targetNavPoint = defenderPosition;
    	}
    	else
    	{
    		targetNavPoint = ctf.getOurFlag().getLocation();
    	}
		
		return true;
    }
    
    private boolean isGetShotWithoutSeenEnemy()
    {
    	if (players.canSeeEnemies() && senses.isShot())
		{
			if (navigation.isNavigating())
			{
				navigation.stopNavigation();
			}
			
			if (!navigation.isNavigating())
			{
				move.turnHorizontal(180);
			}
			
			return true;
		}
    	
    	return false;
    }
    
    private void sendMsgToStealers(String msg)
    {
    	// MSG Init
    	TCRoleStealer stealer = new TCRoleStealer(info.getId(), msg);
    	// MSG set variables
    	stealer.setEnemyFlagLocation(stealedEnemyFlagLocationSend);
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
    	defender.setOuFlagLocation(stealedOurFlagLocationSend);
    	defender.setID(botNumber);
    	defender.setCurrentLocation(info.getLocation());
    	
    	// MSG send
    	tcClient.sendToTeam(defender);
    }
    
    private void sendMsgToTeam(String msg)
    {
    	// MSG Init
    	TCHello team = new TCHello(info.getId(), msg);
    	// MSG set variables
    	
    	// MSG send
    	tcClient.sendToTeam(team);
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
			move.jump();
		}
    	
    	return false;
    }
    
    /**
	 * Returns weapons that the bot does not have or are not loaded.
	 * @param requiredWeapons
	 * @return
	 */
	private Set<UT2004ItemType> filterNotLoaded(UT2004ItemType[] requiredWeapons)
	{
		Set<UT2004ItemType> result = new HashSet<UT2004ItemType>();
		for (UT2004ItemType weapon : requiredWeapons)
		{
    		if (!weaponry.hasPrimaryLoadedWeapon(weapon))
    		{
    			result.add(weapon);
    		}
    	}
		return result;
	}
    
	private boolean shooting()
	{
		// BOT can see enemies
		if (players.canSeeEnemies())
    	{
			if ((info.getCurrentWeaponType().equals(UT2004ItemType.SHOCK_RIFLE)
					&& weaponry.hasAmmoForWeapon(UT2004ItemType.SHOCK_RIFLE))
					|| (info.getCurrentWeaponType().equals(UT2004ItemType.LIGHTNING_GUN)
					&& weaponry.hasAmmoForWeapon(UT2004ItemType.LIGHTNING_GUN)))
			{
				if (navigation.isNavigating())
				{
					navigation.stopNavigation();
				}

				if (!navigation.isNavigating())
				{
					navigation.setFocus(players.getNearestVisibleEnemy());
					shoot.shoot(weaponPrefs, players.getNearestVisibleEnemy());	
				}
			}
			
			if (RANGE_HIGH < fwMap.getDistance(navigation.getNearestNavPoint(players.getNearestVisibleEnemy()), info.getNearestNavPoint()))
			{
				if (move.isRunning())
				{
					move.setWalk();					
				}
				
				navigation.setFocus(players.getNearestVisibleEnemy());
				shoot.shoot(weaponPrefs, players.getNearestVisibleEnemy());
			}

			return true;
    	}

		// BOT can't see enemies
    	if (info.isShooting())
    	{
    		shoot.stopShooting();    			
    	}
    	
    	if (info.isWalking())
    	{
    		move.setRun();    			
    	}
    	
    	return false;
	}
	
    private boolean pickUpItemViaDistance()
    {
    	
    	double distance = Double.MAX_VALUE;
    	Item item = info.getNearestVisibleItem();
    	
    	if (item != null
    			&& item.getNavPoint() != null
    			&& !weaponry.hasLoadedWeapon(item.getType()))
    	{
    		distance = info.getDistance(item);
    	}
    	  	
    	if (distance < DISTANCE_PICK_UP_ITEM_FOR_FLAGSTEALER)
    	{
    		if (stealer)
    		{
    			// go for nearest visible item
    			targetNavPoint = info.getNearestVisibleItem().getNavPoint();
    		}
    		else
    		{
    			// send position of visible item
    			visibleSpawnedItemSend = item.getId(); 
    		}
    		
    		return true;
    	}
    	
    	return false;
    }

    private boolean runForFlag()
    {
    	if (getItemViaDistanceAndUrgentHealth())
    	{
    		return false;
    	}
    	
    	// Navigation to enemy base for FLAG
    	targetNavPoint = ctf.getEnemyBase();
    	return true;
    }
    
    private boolean returnHome()
    {
    	if (getItemViaDistanceAndUrgentHealth())
    	{
    		return true;
    	}
    	
    	targetNavPoint = ctf.getOurBase();
        return true;
    }
    
    private void navigate(ILocated location)
    {
    	navigationStandard(location);
    }
    
    private boolean getItemViaDistanceAndUrgentHealth()
    {
    	// Search ITEMs in BOT's near distance
    	if (pickUpItemViaDistance())
    	{
    		return true;
    	}
    	
    	// BOT needs urgent pick up health
    	if (needHealthUrgent())
    	{
    		if (pickupNearestHealth())
    		{
    			return true;
    		}
    	}
    	
    	return false;
    }

    private void navigationStandard(ILocated location)
    {
    	navigation.navigate(location); 
    }

    private boolean combatDefender()
    {
    	// BOT is shooting
    	if (shooting())
    	{
    		if (players.getNearestVisibleEnemy() != null)
    		{
    			pursueEnemy.heat();
    			navigation.navigate(players.getNearestVisibleEnemy());
    		}
    		
    		return true;
    	}
    	
    	// BOT isn't shooting
    	return false;
    }
    
    private boolean needHealthUrgent()
    {
        return info.getHealth() < 20 || (info.getHealth() + info.getArmor()) < 40;
    }
    
    private boolean pickupNearestHealth()
    {
        if (navigateToItemType(UT2004ItemType.HEALTH_PACK))
        {
        	return true;
        }
        
        return false;
    }
    
    private boolean navigateToItemType(UT2004ItemType type)
    {
        if (navigation.isNavigatingToItem() && navigation.getCurrentTargetItem().getType() == type)
        {
        	return true;
        }
        
        Item item = fwMap.getNearestItem(items.getSpawnedItems(type).values(), navPoints.getNearestNavPoint());        
        
        if (item == null)
        {
            log.warning("No " + type.getName() + " to run to...");
            return false;
        }
        
        if (item.getLocation() == null)
        {
            log.warning("No location " + type.getName() + " to run to...");
            return false;
        }
        
        targetNavPoint = item.getNavPoint();
        return true;
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
    		if (botNumber < 2)
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
    	else
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
    	
    	return tmp;
    }
    
    private void drawPath(List<? extends ILocated> path, Color color) {
    	log.warning("DRAWING PATH, size = " + path.size());
    	draw.setColor(color);
    	for (int i = 1; i < path.size(); ++i) {
    		draw.drawLine(path.get(i-1),  path.get(i));
    	}
    	log.warning("PATH DRAWN!");
    }
    
    public static void main(String args[]) throws PogamutException, Exception
    {
    	initialize(args);
    	
    	// Start TC (~ TeamCommunication) Server first...
    	
    	//tcServer = UT2004TCServer.startTCServer(address, 3001);
    	tcServer = UT2004TCServer.startTCServer();
    	
    	// Start bots
    	new UT2004BotRunner(TeamCommBot.class, "TCBot", address, PORT).setLogLevel(Level.WARNING).startAgents(teamBotsCount);
        //new UT2004BotRunner(TeamCommBot.class, "TCBot").setMain(true).setLogLevel(Level.WARNING).startAgents(teamBotsCount);       
    }
    
//    @Override 
//    public Initialize getInitializeCommand() 
//    {
//    this.botId = BOT_ID_COUNTER++;
//
//    Initialize init = new Initialize();
//    init.setName(BOT_NAME_PREFIX + this.botId);
//    init.setTeam(MKBot.param_team);
//    init.setDesiredSkill(MKBot.param_skill); 
//
//    return init;
//    }
}
