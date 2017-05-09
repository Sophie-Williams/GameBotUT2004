package com.mycompany.tcbot;

import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensomotoric.Weapon;
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
	private static final String GET_SHOT_CANT_SEE_ENEMY =  " ... get sho and can't see enemy!";
	private static final String ENEMY_STEALER_IS_VISIBLE = " ... see stolen flag!";
	private static final String ENEMY_STEALER_IS_NOT_VISIBLE = " ... some enemy stolen flag!";
	private static final String PICKUP_HEALT = " ... pick up health!";
	private static final String PICKUP_WEAPON = " ... pick up weapon!";
	private static final String PICKUP_AMMO = " ... pick up ammo!";
	private static final String RUN_BEHIND_FLAG_INVISIBLE = " ... run behind flag! (INVISIBLE)";
	private static final String RUN_BEHIND_FLAG_VISIBLE = " ... run behind flag! (VISIBLE)";
	private static final String WANT_KILL_ENEMY_STEALER_TRUE = " ... want kill enemy stealer! (score = TRUE)";
	private static final String WANT_KILL_ENEMY_STEALER_FALSE = " ... want kill enemy stealer! (score = FALSE)";
	
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
	private Location stealedEnemyFlagLocationSend = null;
	private Location stolenEnemyFlagLocationRecv = null;
	
	// MSGs data for DEFENDERS
	private Location stolenOurFlagLocationSend = null;
	private Location stolenOurFlagLocationRecv = null;
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
    private static final int DISTANCE_PICK_UP_ITEM_FOR_STEALER = 800;
    private static final int DISTANCE_PICK_UP_ITEM_FOR_DEFENDER = 4000;
	
	private static int number = 0;
	private int botNumber;
	private boolean stealer = false;
	
	private Heatup pursueEnemy = new Heatup(3000);
	
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
        weaponPrefs.addGeneralPref(UT2004ItemType.SHOCK_RIFLE, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.MINIGUN, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.MINIGUN, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.LINK_GUN, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.LINK_GUN, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.FLAK_CANNON, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.FLAK_CANNON, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.ASSAULT_RIFLE, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.ASSAULT_RIFLE, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.ROCKET_LAUNCHER, true);
        weaponPrefs.addGeneralPref(UT2004ItemType.SHIELD_GUN, false);
        weaponPrefs.addGeneralPref(UT2004ItemType.BIO_RIFLE, true);
        
        weaponPrefs.newPrefsRange(RANGE_LOW)
        .add(UT2004ItemType.BIO_RIFLE, true)
        .add(UT2004ItemType.LINK_GUN, false)
        .add(UT2004ItemType.ASSAULT_RIFLE, true)
        .add(UT2004ItemType.SHIELD_GUN, false);
		// Only one weapon is added to this close combat range and it is SHIELD GUN		
			
		// Second range class is from 80 to 1000 ut units (its always from the previous class to the maximum
		// distance of actual class
		weaponPrefs.newPrefsRange(RANGE_MIDDLE)
		.add(UT2004ItemType.SHOCK_RIFLE, false)
		.add(UT2004ItemType.BIO_RIFLE, true)
		.add(UT2004ItemType.LINK_GUN, true)
		.add(UT2004ItemType.FLAK_CANNON, true)
        .add(UT2004ItemType.MINIGUN, false)
        .add(UT2004ItemType.ASSAULT_RIFLE, true);
		// More weapons are in this class with FLAK CANNON having the top priority		
				
		// Third range class is from 1000 to 4000 ut units - that's quite far actually
		weaponPrefs.newPrefsRange(RANGE_HIGH)				
		.add(UT2004ItemType.ROCKET_LAUNCHER, true)
		.add(UT2004ItemType.FLAK_CANNON, false)
        .add(UT2004ItemType.SHOCK_RIFLE, true)
        .add(UT2004ItemType.MINIGUN, false)
		.add(UT2004ItemType.ASSAULT_RIFLE, false);        
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
	}
    
    @EventListener(eventClass=TCRoleDefender.class)
	public void hello(TCRoleDefender hello)
    {
    	if (defenders != null)
    	{
    		defenders.put(hello.getID(), hello);
    	}
    	
    	nearestEnemyBotLocationRecv = hello.getNearestEnemyBotLocation();
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
//    	ctf.getEnemyFlag().isVisible()
//    	Taboo - casovani itemu kdy je sebral - ostatnim preposlu UnrealId nebo ObjectId aby si tp pridali do sveho setu
    	
    	// TODO - orientation point
    	connectionToTC();
    	getNearestDistanceOurStolenFlag();
    	getStoleEnemyFlagPosition();
    	
    	sendMsgToStealers("MSG from: " + getName());    		
    	sendMsgToDefenders("MSG from: " + getName());    		

    	// ********** CODE FOR STEALER
    	if (stealer)
    	{
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
    	
//    	List<NavPoint> nearest = fwMap.getPath(info.getNearestNavPoint(), navigation.getNearestNavPoint(targetNavPoint));
//    	draw.clearAll();
//    	drawPath(nearest, Color.WHITE);
    }
    
    private void getStoleEnemyFlagPosition()
    {
    	for (Map.Entry<Integer, TCRoleStealer> stealer : stealers.entrySet())
    	{
    		if (stealer.getValue().getEnemyFlagLocation() != null)
    		{
    			stolenEnemyFlagLocationRecv = stealer.getValue().getEnemyFlagLocation();
    		}
    	}
    }
    
    private void getNearestDistanceOurStolenFlag()
    {
    	double distance = Double.MAX_VALUE;
    	Location tmpLocation = null;
    	
    	if (defenders == null)
    	{
    		return;
    	}
    	
    	for (Map.Entry<Integer, TCRoleDefender> defender : defenders.entrySet())
    	{
    		if (defender.getValue().getOurFlagLocation() != null)
    		{
    			double tmpDistance = fwMap.getDistance(navPoints.getNearestNavPoint(defender.getValue().getOurFlagLocation()), ctf.getEnemyBase());
    			if (tmpDistance < distance)
    			{
    				tmpLocation = defender.getValue().getOurFlagLocation();
    				distance = tmpDistance;
    			}
    		}
    	}
    	
    	for (Map.Entry<Integer, TCRoleStealer> stealer : stealers.entrySet())
    	{
    		if (stealer.getValue().getOurFlagLocation() != null)
    		{
    			double tmpDistance = fwMap.getDistance(navPoints.getNearestNavPoint(stealer.getValue().getOurFlagLocation()), ctf.getEnemyBase());
    			if (tmpDistance < distance)
    			{
    				tmpLocation = stealer.getValue().getOurFlagLocation();
    				distance = tmpDistance;
    			}
    		}
    	}
    	
    	if (tmpLocation != null)
    	{
    		stolenOurFlagLocationRecv = tmpLocation;
    	}
    	else
    	{
    		stolenOurFlagLocationRecv = null;
    	}
    }
    
    private boolean pickUpItemsViaDistanceAndCategory(int distanceTreshold)
    {
    	if (pickUpItemViaUTypeAndDistance(UT2004ItemType.U_DAMAGE_PACK, distanceTreshold))
    	{
    		bot.getBotName().setInfo(" ... pick up double damage!");
    		return true;
    	}
    	
    	if (info.getHealth() < 100)
    	{
    		if (pickUpItemViaCategoryAndDistance(Category.HEALTH, distanceTreshold))
    		{
    			bot.getBotName().setInfo(PICKUP_HEALT);
    			return true;
    		}
    	}

    	if (pickUpNearestWeapon(distanceTreshold))
    	{
    		bot.getBotName().setInfo(PICKUP_WEAPON);
    		return true;
    	}
    	
    	if (pickUpNearestAmmo(distanceTreshold))
    	{
    		bot.getBotName().setInfo(PICKUP_AMMO);
    		return true;
    	}
    	
    	if (info.getArmor() < 100)
    	{
    		if (pickUpItemViaCategoryAndDistance(Category.ARMOR, distanceTreshold))
    		{
    			bot.getBotName().setInfo(" ... pick up shield!");
    			return true;
    		}
    	}
    	
		return false;
    }
    
    private boolean pickUpItemViaUTypeAndDistance(UT2004ItemType type, int distanceTreshold)
    {
    	Item item = items.getNearestVisibleItem(type);
    	
    	if (item != null
    			&& fwMap.getDistance(info.getNearestNavPoint(), item.getNavPoint())
				< distanceTreshold)
    	{
    		targetNavPoint = item.getLocation();
    		return true;
    	}
    	
    	return false;
    }
    
    private boolean pickUpItemViaCategoryAndDistance(Category category, int distanceTreshold)
    {
    	Item item = items.getNearestVisibleItem(category);
    	
		if (item != null
				&& fwMap.getDistance(info.getNearestNavPoint(), item.getNavPoint())
					< distanceTreshold)
		{
			targetNavPoint = item.getLocation();
			return true;
		}
		
		return false;
    }
    
    private boolean pickUpNearestAmmo(int distanceTreshold)
    {
    	Map<Double, Item> nearestAmmoForWeapon = new TreeMap<Double, Item>();
    	
    	for (Map.Entry<ItemType, Weapon> weapon : weaponry.getLoadedWeapons().entrySet())
    	{
    		Item itemPri = items.getNearestVisibleItem(weapon.getValue().getDescriptor().getPriAmmoItemType());
    		Item itemSec = items.getNearestVisibleItem(weapon.getValue().getDescriptor().getSecAmmoItemType());
    		
    		if (itemPri != null)
    		{
    			double distancePri = fwMap.getDistance(info.getNearestNavPoint(), itemPri.getNavPoint());
    			nearestAmmoForWeapon.put(distancePri, itemPri);    			
    		}
    		
    		if (itemSec != null)
    		{
    			double distanceSec = fwMap.getDistance(info.getNearestNavPoint(), itemSec.getNavPoint());
    			nearestAmmoForWeapon.put(distanceSec, itemSec);
    		}
    	}
    	
    	for (Map.Entry<Double, Item> ammo : nearestAmmoForWeapon.entrySet())
    	{
    		int ammoMax = weaponry.getMaxAmmo(ammo.getValue().getType());
    		int ammoHas = weaponry.getAmmo(ammo.getValue().getType());
    		
    		if (ammoHas < ammoMax / 2)
    		{
    			if (distanceTreshold > fwMap.getDistance(ammo.getValue().getNavPoint(), navPoints.getNearestNavPoint(info.getLocation())))
    			{
    				targetNavPoint = ammo.getValue().getLocation();
    				return true;
    			}
    		}
    	}
    	
    	return false;
    }
    
    private boolean pickUpNearestWeapon(int distanceTreshold)
    {
    	Map<UnrealId, Item> weapons = items.getAllItems(Category.WEAPON);
    	double nearestDistance = Double.MAX_VALUE;
    	
    	for (Map.Entry<UnrealId, Item> weapon : weapons.entrySet())
    	{
    		if (!weaponry.hasWeapon(weapon.getValue().getType()))
    		{
    			double itemDistance = fwMap.getDistance(info.getNearestNavPoint(), weapon.getValue().getNavPoint());
    			if (itemDistance < distanceTreshold)
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
    	if (!navigation.isNavigating()
    			&& !players.canSeeEnemies()
    			&& !info.isShooting())
    	{
    		move.turnHorizontal(90);
    	}
    }
    
    private boolean stealerBehaviour()
    {
    	// communication values calibration
    	stealedEnemyFlagLocationSend = null;
    	stolenOurFlagLocationSend = null;
    	
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
    	}
    	
    	if (!ctf.isEnemyFlagHome())
    	{
    		// STEALER *** HAVE FLAG, RUN HOME
    		if (ctf.isBotCarryingEnemyFlag())
    		{
    			scoreIfIsItPossible();
    		}
    		
    		// STEALER *** HAVE NOT FLAG, OTHER BOT HAS FLAG
    		if (!ctf.isBotCarryingEnemyFlag())
    		{
    			if (stolenEnemyFlagLocationRecv != null)
    			{
    				bot.getBotName().setInfo(RUN_BEHIND_FLAG_INVISIBLE);
					targetNavPoint = navPoints.getNearestNavPoint(stolenEnemyFlagLocationRecv);
    			}
    			else
    			{
    				if (!ctf.isOurFlagHome())
    				{
    					searchOurFlag();    						
    				}
    				else
    				{
    					if (ctf.getEnemyFlag().getLocation() != null)
    					{
    						bot.getBotName().setInfo(RUN_BEHIND_FLAG_VISIBLE);
    						targetNavPoint = ctf.getEnemyFlag().getLocation();
    					}
    					else
    					{
    						bot.getBotName().setInfo(WANT_KILL_ENEMY_STEALER_FALSE);
    						targetNavPoint = ctf.getOurBase().getLocation();    						    						
    					}
    				}
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
    	return true;
    }
    
    private boolean defenderBehaviour()
    {
    	// communication values calibration
    	stolenOurFlagLocationSend = null;
    	
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

		// Some enemy bot stolen our flag
		if (!ctf.isOurFlagHome())
		{
			searchOurFlag();
		}
		
		return false;
    }
    
    private void searchOurFlag()
    {
    	if (ctf.getOurFlag().getLocation() == null)
		{
			if (stolenOurFlagLocationRecv != null)
			{
				bot.getBotName().setInfo(ENEMY_STEALER_IS_VISIBLE);
				targetNavPoint = stolenOurFlagLocationRecv;
				
				if (navPoints.getNearestNavPoint(info.getLocation()).equals(navPoints.getNearestNavPoint(stolenOurFlagLocationRecv)))
				{
					bot.getBotName().setInfo(ENEMY_STEALER_IS_VISIBLE + " - OLD INFO");
					targetNavPoint = ctf.getEnemyBase().getLocation();
				}
			}
			else
			{
				bot.getBotName().setInfo(ENEMY_STEALER_IS_NOT_VISIBLE);
				targetNavPoint = ctf.getEnemyBase().getLocation();
			}
		}
		else
		{
			bot.getBotName().setInfo(ENEMY_STEALER_IS_VISIBLE);
			targetNavPoint = ctf.getOurFlag().getLocation();
			stolenOurFlagLocationSend = ctf.getOurFlag().getLocation();
		}
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
    		if (pickUpItemsViaDistanceAndCategory(DISTANCE_PICK_UP_ITEM_FOR_DEFENDER))
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
//			move.jump();
		}
    	
    	return false;
    }
    
	private boolean shooting()
	{
		// BOT can see enemies
		if (players.canSeeEnemies())
    	{
			nearestEnemyBotLocationSend = players.getNearestVisibleEnemy().getLocation();

			pursueEnemy.heat();
			navigation.setFocus(nearestEnemyBotLocationSend);
			shoot.shoot(weaponPrefs, nearestEnemyBotLocationSend);

			return true;
    	}
		
		// BOT can't see enemies
		if (!players.canSeeEnemies())
		{
			nearestEnemyBotLocationSend = null;
			
			if (nearestEnemyBotLocationRecv != null)
			{
				navigation.setFocus(nearestEnemyBotLocationRecv);
			}
			
			if (info.isShooting())
			{
				shoot.stopShooting();    			
			}
		}
    	
    	return false;
	}

    private boolean runForFlag()
    {
    	if (pickUpItemsViaDistanceAndCategory(DISTANCE_PICK_UP_ITEM_FOR_STEALER))
    	{
    		return false;
    	}
    	
    	// Navigation to enemy base for FLAG
    	targetNavPoint = ctf.getEnemyBase();

    	return true;
    }
    
    private boolean returnHome()
    {
    	bot.getBotName().setInfo(RUN_HOME_WITH_FLAG);
    	targetNavPoint = ctf.getOurBase().getLocation();
    	stealedEnemyFlagLocationSend = info.getLocation();
        return true;
    }
    
    private void navigate(ILocated location)
    {
    	navigation.navigate(location); 
    	info.atLocation(location, 30);
    }

    private boolean combatDefender()
    {
    	// BOT is shooting
    	if (shooting())
    	{
    		return true;
    	}
    	
    	// BOT isn't shooting
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
    
    private void removeEdgesAccordingMaps(String mapName)
    {
    	// TODO
    	// Example: navBuilder.removeEdge("PathNode35", "JumpSpot1");
    	
    	if (MAP_NAME_BP2.equals(mapName))
        {
    		// ***** RED
    		// middle
    		navBuilder.removeEdge("AssaulPath12", "PathNode74");
    		navBuilder.removeEdge("AssaulPath12", "PathNode81");
    		// right
    		navBuilder.removeEdge("JumpSpot2", "PathNode74");
    		// left
    		navBuilder.removeEdge("InventorySpot59", "PathNode81");
//    		navBuilder.removeEdge("PathNode58", "JumpSpot1");
    		
    		// *** BLUE
    		// middle
    		navBuilder.removeEdge("AssaulPath5", "PathNode44");
    		navBuilder.removeEdge("AssaulPath5", "PathNode0");
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
    		
//    		navBuilder.removeEdgesBetween("PathNode26", "PathNode23");
//    		navBuilder.removeEdgesBetween("PathNode38", "PathNode23");
    	}
    	if (MAP_NAME_MAUL.equals(mapName))
    	{
    		
    	}
    }
    
//    private void drawPath(List<? extends ILocated> path, Color color) {
//    	log.warning("DRAWING PATH, size = " + path.size());
//    	draw.setColor(color);
//    	for (int i = 1; i < path.size(); ++i) {
//    		draw.drawLine(path.get(i-1),  path.get(i));
//    	}
//    	log.warning("PATH DRAWN!");
//    }
    
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
