package com.mycompany.tcbot;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;

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
	private ILocated ourStealedFlagLocationSend = null;
	private ILocated ourStealedFlagLocationRecv = null;
	
	// TMP msg for showing communication between Bots
	private TreeMap<Integer, TCRoleDefender> defenders;
	private TreeMap<Integer, TCRoleStealer> stealers;

	// Weapons ranges
	private static final int RANGE_LOW = 50;
    private static final int RANGE_MIDDLE = 500;
    private static final int RANGE_HIGH = 1000;
    private static final int RANGE_SUPERHIGH = 4000;
    private static final int DISTANCE_PICKU_UP_ITEM_FOR_FLAGSTEALER = 600;
	
	private static int number = 0;
	private int botNumber;
	private boolean stealer = false;
	
	private boolean useCoverPath = false;
	private boolean usingCoverPath = false;
	
	private ILocated ourFlagLocationOrigin;
	private ILocated enemyFlagLocationOrigin;
	
	private int mainStealer;
	private int mainDefender;
	
	Heatup pursueEnemy = new Heatup(3000);
	
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
    	
    	if (hello.getOuFlagLocation() != null)
    	{
    		ourStealedFlagLocationRecv = hello.getOuFlagLocation();
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
    	notMoveturnAround();
    	
    	
    	// TODO - nastavit sber zbrani podle vzdalenosti pro defendera - momentalne to bere moc casu
    	// TODO - pridat sber munice kdyz nemas (jinak nestrili)
    	// TODO - pouzivat heatup a cooldown = prodlouzeni akce
    	// TODO - zkusit vzdy bezet naplno
    	// TODO - zkusit prenastavit coverpath
    }
    
    private void notMoveturnAround()
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
//    		return true;
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
    	if (ctf.canBotScore())
		{
			returnHome();
			useCoverPath = false;
		}
		else
		{
			useCoverPath = true;
		}
    	
    	stealedEnemyFlagLocationSend = info.getNearestNavPoint().getLocation();
    	return useCoverPath;
    }
    
    private boolean defenderBehaviour()
    {
    	if (combatDefender())
		{
    		// TODO - nebezet za souperem pokud nema nasi vlajku !!!
			bot.getBotName().setInfo(COMBAT);
//			return true;
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
    	if (isGetShotWithoutSeenEnemy())
		{
    		bot.getBotName().setInfo(GET_SHOT_CANT_SEE_ENEMY);
			return false;
		}
		
		if (pickupSomeWeapon())
		{
			bot.getBotName().setInfo(PICKUP_WEAPONS);
			return false;
		}
		
		// Go on defenders positions
		targetNavPoint = defenderPosition;
		
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
    	defender.setOuFlagLocation(ourStealedFlagLocationSend);
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
    	  	
    	if (distance < DISTANCE_PICKU_UP_ITEM_FOR_FLAGSTEALER)
    	{
    		targetNavPoint = info.getNearestVisibleItem().getNavPoint();
    		
    		return true;
    	}
    	
    	return false;
    }
    
    /**
     * Translates 'types' to the set of "nearest spawned items" of those 'types'.
     * @param types
     * @return
     */
    private Set<Item> getNearestSpawnedItems(Collection<UT2004ItemType> types) {
    	Set<Item> result = new HashSet<Item>();
    	for (UT2004ItemType type : types) {
    		Item n = getNearestSpawnedItem(type);
    		if (n != null) {
    			result.add(n);
    		}
    	}
    	return result;
    }
    
    /**
     * Returns the nearest spawned item of 'type'.
     * @param type
     * @return
     */
    private Item getNearestSpawnedItem(UT2004ItemType type) {
    	final NavPoint nearestNavPoint = info.getNearestNavPoint();
    	Item nearest = DistanceUtils.getNearest(
    			items.getSpawnedItems(type).values(), 
    			info.getNearestNavPoint(),
    			new DistanceUtils.IGetDistance<Item>() {
					@Override
					public double getDistance(Item object, ILocated target) {
						return fwMap.getDistance(object.getNavPoint(), nearestNavPoint);
					}
    		
    	});
    	return nearest;
    }
    
    private boolean runForFlag()
    {
    	if (itemViaDistanceAndUrgentHealth())
    	{
    		return false;
    	}
    	
    	// Navigation to enemy base for FLAG
    	targetNavPoint = ctf.getEnemyBase();
    	return true;
    }
    
    private boolean returnHome()
    {
    	if (itemViaDistanceAndUrgentHealth())
    	{
    		return true;
    	}
    	
    	targetNavPoint = ctf.getOurBase();
        return true;
    }
    
    private void navigate(ILocated location)
    {
    	if (useCoverPath)
    	{
    		navigationCoverPath(location);
    	}
    	else
    	{
    		navigationStandard(location);
    	}
    }
    
    private boolean itemViaDistanceAndUrgentHealth()
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

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void navigationCoverPath(ILocated location)
    {
    	PrecomputedPathFuture<NavPoint> path = generateCoverPath(location);
    	if (path == null)
    	{
    		log.info(getName() + " ... could not generate COVER PATH!");
    		navigationStandard(location);
    		return;
    	}
    	
    	log.info(getName() + " ... running along COVER PATH!");
    	usingCoverPath = true;

    	navigation.navigate((IPathFuture)path);
    }
    
    private PrecomputedPathFuture<NavPoint> generateCoverPath(ILocated runningTo) {
    	NavPoint startNav = info.getNearestNavPoint();
    	NavPoint targetNav = navigation.getNearestNavPoint(runningTo);
    	
    	AStarResult<NavPoint> result = aStar.findPath(startNav, targetNav, new CoverMapView());
    	
    	PrecomputedPathFuture<NavPoint> pathFuture = new PrecomputedPathFuture<NavPoint>(startNav, targetNav, result.getPath());
    	
    	return pathFuture;
    }
    
    private void navigationStandard(ILocated location)
    {
    	usingCoverPath = false;
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
    
    private boolean pickupSomeWeapon()
    {
        if (!weaponry.hasLoadedWeapon(UT2004ItemType.SHOCK_RIFLE))
        {
            if (navigateToItemType(UT2004ItemType.SHOCK_RIFLE))
            {
            	return true;
            }
        }
        
        if (!weaponry.hasLoadedWeapon(UT2004ItemType.MINIGUN))
        {
            if (navigateToItemType(UT2004ItemType.MINIGUN))
            {
            	return true;
            }
        }
        
        if (!weaponry.hasLoadedWeapon(UT2004ItemType.LINK_GUN))
        {
            if (navigateToItemType(UT2004ItemType.LINK_GUN))
            {
            	return true;
            }
        }
        
        return false;
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
    
    private class CoverMapView implements IPFMapView<NavPoint> {

		@Override
		public Collection<NavPoint> getExtraNeighbors(NavPoint node, Collection<NavPoint> mapNeighbors) {
			return null;
		}

		@Override
		public int getNodeExtraCost(NavPoint node, int mapCost) {
                    int penalty = 0;
                    
                    for (Player player : players.getVisiblePlayers().values())
                    {
                        if (visibility.isVisible(node, player.getLocation()))
                        {
                            penalty += 100;
                        }
                    }
                    return 0;
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
	        if ((link.getFlags() & FloydWarshallMap.BAD_EDGE_FLAG) > 0)
	        {
	            return false;
	        }
                
			return true;
		}
    	
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
