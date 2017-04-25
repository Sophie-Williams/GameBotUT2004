package com.mycompany.tcbot;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import cz.cuni.amis.pogamut.base.communication.translator.event.IWorldChangeEvent;
import cz.cuni.amis.pogamut.base.communication.worldview.event.IWorldEvent;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base.utils.math.DistanceUtils;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.AgentInfo;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
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
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessage;
import cz.cuni.amis.pogamut.ut2004.teamcomm.server.UT2004TCServer;
import cz.cuni.amis.pogamut.ut2004.utils.UT2004BotRunner;
import cz.cuni.amis.utils.Cooldown;
import cz.cuni.amis.utils.collections.MyCollections;
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
public class TeamCommBot extends UT2004BotTCController<UT2004Bot> {

	// Do I use cover path?
    //private boolean usingCoverPath = false;
    
    // Target navigation point of bot way
    private ILocated targetNavPoint = null;
    
    // Must I use cover path? (properties settings)
    //private boolean useCoverPath = false;
    
    // Next navigation point for navigate
    //private NavPoint runningToNavPoint = null;
	
    private static final int BOTS_COUNT = 5;
	private static final double DISTANCE_PICKU_UP_ITEM_FOR_FLAGSTEALER = 100;
	private static String[] names = new String[]{"Tupec", "Tupec", "Tupec", "Tupec", "Tupec", "Tupec", "Tupec", "Tupec"};
	
	// MSGs data for STEALERS
	// TODO - mandatory variables
	
	// MSGs data for DEFENDERS
	// TODO - mandatory variables
	
	// MSGs data for ALL/TCHello
	// TODO - mandatory variables/not all
	
	// TMP msg for showing communication between Bots
	private String lastMsg;
	
	static {
		List<String> n = MyCollections.toList(names);
		Collections.shuffle(n);
		names = n.toArray(new String[n.size()]);
	}
	
	private static int number = 0;
	private int myNumber;
	private boolean stealer;
	
    @Override
    public Initialize getInitializeCommand() {
    	myNumber = ++number;
    	stealer = myNumber % 2 == 0 ? true : false;
        return new Initialize().setName(names[(myNumber) % names.length] + (myNumber < 3 ? "-RED" : "-BLUE")).setTeam(myNumber < 3 ? AgentInfo.TEAM_RED : AgentInfo.TEAM_BLUE);
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
        
        weaponPrefs.newPrefsRange(50)
        .add(UT2004ItemType.SHIELD_GUN, true);
		// Only one weapon is added to this close combat range and it is SHIELD GUN		
			
		// Second range class is from 80 to 1000 ut units (its always from the previous class to the maximum
		// distance of actual class
		weaponPrefs.newPrefsRange(500)
		        .add(UT2004ItemType.FLAK_CANNON, true)
		        .add(UT2004ItemType.MINIGUN, true)
		        .add(UT2004ItemType.LINK_GUN, false)
		        .add(UT2004ItemType.ASSAULT_RIFLE, true);        
		// More weapons are in this class with FLAK CANNON having the top priority		
				
		// Third range class is from 1000 to 4000 ut units - that's quite far actually
		weaponPrefs.newPrefsRange(2000)
		        .add(UT2004ItemType.SHOCK_RIFLE, true)
		        .add(UT2004ItemType.MINIGUN, false);
		// Two weapons here with SHOCK RIFLE being the top
				
		// The last range class is from 4000 to 100000 ut units. In practise 100000 is
		// the same as infinity as there is no map in UT that big
		weaponPrefs.newPrefsRange(50000)
		        .add(UT2004ItemType.LIGHTNING_GUN, true)
		        .add(UT2004ItemType.SHOCK_RIFLE, true);  	  
		// Only two weapons here, both good at sniping
    }
    
    // ======================
    // CLIENT DEFINED MESSAGE
    // ======================
    
    /**
     * If messages you're sending implements {@link IWorldChangeEvent} and {@link IWorldEvent}, you can listen to them directly.
     * @param hello
     */
    @EventListener(eventClass=TCHello.class)
	public void hello(TCHello hello) {
		//log.info("@EventListener(TCHello): " + hello.getWho().getStringId() + " says '" + hello.getMsg() + "'");
    	lastMsg = hello.getMsg();
	}
    
    @EventListener(eventClass=TCRoleStealer.class)
	public void hello(TCRoleStealer hello) {
		//log.info("@EventListener(TCRoleStealer): " + hello.getWho().getStringId() + " says '" + hello.getMsg() + "'");
    	lastMsg = hello.getMsg();
	}
    
    @EventListener(eventClass=TCRoleDefender.class)
	public void hello(TCRoleDefender hello) {
		//log.info("@EventListener(TCRoleDefender): " + hello.getWho().getStringId() + " says '" + hello.getMsg() + "'");
    	lastMsg = hello.getMsg();
	}
    
    // =====
    // LOGIC
    // =====
    
    Cooldown msgCooldown = new Cooldown(3000);
    int msgNum = 0;
    
    int myChannelId = -1;
    
    @Override
    public void beforeFirstLogic()
    {
    	// TODO - prepare data before first logic
    }
    
    @Override
    public void logic() throws PogamutException
    {	    
    	connectionToTC();
    	
    	sendMsgToTeam("Nazdar dementi ... !!!");
    	sendMsgToStealers("Nazdar stealers ... !!!");
    	sendMsgToDefenders("Nazdar defenders ... !!!");
    	
    	recievMsg();
    	
    	// ********** CODE FOR STEALER
    	if (stealer)
    	{
    		// Bot where damaged
    		if (senses.isShot())
    		{
    			log.info("Bot where damaged ...");
    		}
        	
        	// Bot is hearing noise
    		if (senses.isHearingNoise())
    		{
    			log.info("Bot is hearing noise ...");
    		}
        	
        	if (combatStealer())
        	{
        		return;
        	}
    		
        	if (ctf.isEnemyFlagHome())
        	{
        		runForFlag();
        	}
        	else if (ctf.isBotCarryingEnemyFlag())
        	{
        		if (ctf.canBotScore())
        		{
        			returnHome();        			
        		}
        		else
        		{
        			// TODO - useCoverPath
        		}
        	}
        	else
        	{
        		// TODO - don't know what happen
        	}
    	}
    	// ********** CODE FOR DEFENDER
    	else
    	{
    		if (ctf.isOurFlagHome())
    		{
    			if (combatDefender(ctf.getOurBase()))
    			{
    				
    			}
    			else
    			{
    				pickupSomeWeapon();
    				pickupGoodItem();
    			}
    		}
    		else
    		{
    			if (ctf.canEnemyTeamScore())
    			{
    				// TODO - our flag is stealed : enemy flag is home
    			}
    			else
    			{
    				
    			}
    			combatDefender(ctf.getEnemyBase());
    		}
    	}
 
    	navigation.navigate(targetNavPoint);
    	info.atLocation(targetNavPoint, 30);
    }
    
    private void recievMsg()
    {
    	log.info(info.getName() + "_" + info.getId() + "********** MSG **********: " + lastMsg);
    }
    
    private void sendMsgToStealers(String msg)
    {
    	// MSG Init
    	TCRoleStealer stealer = new TCRoleStealer(info.getId(), msg);
    	// MSG set variables
    	
    	// MSG send
    	tcClient.sendToTeam(stealer);
    }

    private void sendMsgToDefenders(String msg)
    {
    	// MSG Init
    	TCRoleDefender defender = new TCRoleDefender(info.getId(), msg);
    	// MSG set variables
    	
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
    	
    	if (myNumber != 1)
    	{
    		return false;
    	}
    	
    	if (tcClient.getConnectedAllBots().size() != BOTS_COUNT)
    	{
    		// wait till all bots get connected...
    		return false;
    	}
    	
    	return true;
    }
    
    private boolean combatStealer()
    {
    	if (shooting())
    	{
    		return true;
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
		if (players.canSeeEnemies())
    	{
			// SHOCK_RIFLE or LIGHTNING_GUN shooting ...
			if (info.getCurrentWeaponType().equals(UT2004ItemType.SHOCK_RIFLE)
					|| info.getCurrentWeaponType().equals(UT2004ItemType.LIGHTNING_GUN))
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
			else
			{
				// Another weapon shooting
				if (move.isRunning())
				{
					move.setWalk();					
				}
				
				navigation.setFocus(players.getNearestVisibleEnemy());
				shoot.shoot(weaponPrefs, players.getNearestVisibleEnemy());
			}

			return true;
    	}

    	if (!players.canSeeEnemies())
    	{
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
    		distance = info.getDistance(item.getNavPoint().getLocation());
    	}
    	  	
    	if (distance < DISTANCE_PICKU_UP_ITEM_FOR_FLAGSTEALER)
    	{
    		log.info("JSEM BLIZKO NEJAKEHO ITEMU!!! - distance: " + distance + " " + info.getNearestVisibleItem().getType().getName());
    		navigate(info.getNearestVisibleItem());
    		
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
    	// Search ITEMs in BOT's near distance
    	if (pickUpItemViaDistance())
    	{
    		return false;
    	}
    	
    	// BOT needs urgent pick up health
    	if (needHealthUrgent())
    	{
    		if (pickupNearestHealth())
    		{
    			return false;
    		}
    	}
    	
    	// Navigation to enemy base for FLAG
    	navigate(ctf.getEnemyBase());
    	return true;
    }
    
    private boolean returnHome()
    {
    	navigate(ctf.getOurBase());
    	
        return true;
    }
    
    private void navigate(ILocated location)
    {
    	targetNavPoint = location;
    }
    
    private boolean combatDefender(NavPoint target)
    {
    	if (players.canSeeEnemies())
    	{
            // navigation to nearest visible enemy
            navigate(players.getNearestVisibleEnemy().getLocation());
            // shooting on nearest visible enemy
            shoot.shoot(weaponPrefs, players.getNearestVisibleEnemy());
            
            return true;
    	}
    	else if (!players.canSeeEnemies() && info.isShooting())
    	{
    		shoot.stopShooting();
    		navigate(target);
    		
    		return false;
    	}
    	else
    	{
    		pickupSomeWeapon();
    		
    		return false;
    	}
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
    
    private boolean pickupGoodItem()
    {
        if (items.getSpawnedItems(UT2004ItemType.SHIELD_PACK).size() > 0)
        {
            if (navigateToItemType(UT2004ItemType.SHIELD_PACK))
            {
            	return true;
            }
        }
        
        if (info.getHealth() < 80)
        {
            if (navigateToItemType(UT2004ItemType.HEALTH_PACK))
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
        
        navigate(item);
        
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
    
    public static void main(String args[]) throws PogamutException {
    	// Start TC (~ TeamCommunication) Server first...
    	 tcServer = UT2004TCServer.startTCServer();
    	
    	// Starts 3 bot
        new UT2004BotRunner(TeamCommBot.class, "TCBot").setMain(true).setLogLevel(Level.WARNING).startAgents(6);       
    }
}
