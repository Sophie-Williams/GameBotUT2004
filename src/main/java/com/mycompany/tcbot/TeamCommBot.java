package com.mycompany.tcbot;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import cz.cuni.amis.pogamut.base.agent.state.impl.AgentState;
import cz.cuni.amis.pogamut.base.communication.translator.event.IWorldChangeEvent;
import cz.cuni.amis.pogamut.base.communication.worldview.event.IWorldEvent;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base.utils.math.DistanceUtils;
import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.AgentInfo;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.AgentStats;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
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
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.server.messages.TCInfoBotJoined;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.server.messages.TCInfoBotLeft;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.server.messages.TCInfoTeamChannelBotJoined;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.server.messages.TCInfoTeamChannelBotLeft;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.server.messages.TCInfoTeamChannelCreated;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.server.messages.TCInfoTeamChannelDestroyed;
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
    private Location targetNavPoint = null;
    
    // Must I use cover path? (properties settings)
    //private boolean useCoverPath = false;
    
    // Next navigation point for navigate
    //private NavPoint runningToNavPoint = null;
	
	private static final double DISTANCE_PICKU_UP_ITEM_FOR_FLAGSTEALER = 100;
	private static String[] names = new String[]{"Tupec", "Tupec", "Tupec", "Tupec", "Tupec", "Tupec", "Tupec", "Tupec"};
	
	static {
		List<String> n = MyCollections.toList(names);
		Collections.shuffle(n);
		names = n.toArray(new String[n.size()]);
	}
	
	private static int number = 0;
	private int myNumber;
	private boolean stealer;
	private Set<UT2004ItemType> missingWeapons;
	private UT2004ItemType[] requiredWeapons;
	
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
        
        weaponPrefs.newPrefsRange(80)
        .add(UT2004ItemType.SHIELD_GUN, true);
		// Only one weapon is added to this close combat range and it is SHIELD GUN		
			
		// Second range class is from 80 to 1000 ut units (its always from the previous class to the maximum
		// distance of actual class
		weaponPrefs.newPrefsRange(1000)
		        .add(UT2004ItemType.FLAK_CANNON, true)
		        .add(UT2004ItemType.MINIGUN, true)
		        .add(UT2004ItemType.LINK_GUN, false)
		        .add(UT2004ItemType.ASSAULT_RIFLE, true);        
		// More weapons are in this class with FLAK CANNON having the top priority		
				
		// Third range class is from 1000 to 4000 ut units - that's quite far actually
		weaponPrefs.newPrefsRange(4000)
		        .add(UT2004ItemType.SHOCK_RIFLE, true)
		        .add(UT2004ItemType.MINIGUN, false);
		// Two weapons here with SHOCK RIFLE being the top
				
		// The last range class is from 4000 to 100000 ut units. In practise 100000 is
		// the same as infinity as there is no map in UT that big
		weaponPrefs.newPrefsRange(100000)
		        .add(UT2004ItemType.LIGHTNING_GUN, true)
		        .add(UT2004ItemType.SHOCK_RIFLE, true);  	  
		// Only two weapons here, both good at sniping
    }
    
    public String toString(TCMessage tcMessage) {
    	StringBuffer sb = new StringBuffer();
    	sb.append(tcMessage.getTarget());
    	switch(tcMessage.getTarget()) {
    	case CHANNEL:
    		sb.append("[");
    		sb.append(tcMessage.getChannelId());
    		sb.append("]");
    		break;
    	}
    	sb.append(" from " + getPlayerName(tcMessage.getSource()));
    	sb.append(" of type ");
    	sb.append(tcMessage.getMessageType().getToken());
    	sb.append(": ");
    	sb.append(String.valueOf(tcMessage.getMessage()));
    	
    	return sb.toString();
    }
    
    // ====================
    // TC Protocol Messages
    // ====================
    
    @EventListener(eventClass=TCInfoBotJoined.class)
    public void tcBotJoined(TCInfoBotJoined botJoined) {
    	log.info("@EventListener(TCInfoBotJoined): Bot " + getPlayerName(botJoined.getBotId()) + " from team " + botJoined.getTeam() + " has joined the TC server.");
    }
    
    @EventListener(eventClass=TCInfoBotLeft.class)
    public void tcBotLeft(TCInfoBotLeft botLeft) {
    	log.info("@EventListener(TCInfoBotLeft): Bot " + getPlayerName(botLeft.getBotId()) + " from team " + botLeft.getTeam() + " has left the TC server.");
    }
    
    @EventListener(eventClass=TCInfoTeamChannelCreated.class)
    public void tcInfoTeamChannelCreated(TCInfoTeamChannelCreated channelCreated) {
    	log.info("@EventListener(TCInfoTeamChannelCreated): New team channel " + channelCreated.getChannel().getChannelId() + " created by " + getPlayerName(channelCreated.getChannel().getCreator()) + ".");
    	
    	if (myNumber > 1) {
    		log.info("Joining channel " + channelCreated.getChannel().getChannelId() + " !");
    		tcClient.requestJoinChannel(channelCreated.getChannel().getChannelId());
    	}
    }
    
    @EventListener(eventClass=TCInfoTeamChannelDestroyed.class)
    public void tcInfoTeamChannelDestroyed(TCInfoTeamChannelDestroyed channelDestroyed) {
    	log.info("@EventListener(TCInfoTeamChannelDestroyed): Team channel " + channelDestroyed.getChannelId() + " destroyed by " + getPlayerName(channelDestroyed.getDestroyer()) + ".");
    }
    
    @EventListener(eventClass=TCInfoTeamChannelBotJoined.class)
    public void tcInfoTeamChannelBotJoined(TCInfoTeamChannelBotJoined botJoined) {
    	log.info("@EventListener(TCInfoTeamChannelBotJoined): Bot " + getPlayerName(botJoined.getBotId()) + " joined team channel " + botJoined.getChannelId() + ".");
    }
    
    @EventListener(eventClass=TCInfoTeamChannelBotLeft.class)
    public void tcInfoTeamChannelBotLeft(TCInfoTeamChannelBotLeft botLeft) {
    	log.info("@EventListener(TCInfoTeamChannelBotLeft): Bot " + getPlayerName(botLeft.getBotId()) + " left team channel " + botLeft.getChannelId() + ".");
    }

    // ============
    // ALL MESSAGES
    // ============
    
    /**
     * You can listen to all {@link TCMessage}s via standard {@link EventListener}.
     * @param hello
     */
    @EventListener(eventClass=TCMessage.class)
    public void tcMessage(TCMessage tcMessage) {
    	log.info("@EventListener(TCMessage): " + toString(tcMessage));
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
		log.info("@EventListener(TCHello): " + hello.getWho().getStringId() + " says '" + hello.getMsg() + "'");
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
    	requiredWeapons = new UT2004ItemType[] {
				UT2004ItemType.ASSAULT_RIFLE,
				UT2004ItemType.SHIELD_GUN,
				UT2004ItemType.LIGHTNING_GUN,
				UT2004ItemType.SHOCK_RIFLE,
				UT2004ItemType.MINIGUN,
				UT2004ItemType.LINK_GUN,
				UT2004ItemType.FLAK_CANNON,
				UT2004ItemType.ASSAULT_RIFLE,
				UT2004ItemType.ROCKET_LAUNCHER,
				UT2004ItemType.SHIELD_GUN,
				UT2004ItemType.BIO_RIFLE,
				};
    	
    	missingWeapons = filterNotLoaded(requiredWeapons);  	
    }
    
    @Override
    public void logic() throws PogamutException
    {	    
    	
    	
    	/*if (!visibility.isInitialized())
        {
    		log.warning("Missing visibility information for the map: " + game.getMapName());
    		body.getCommunication().sendGlobalTextMessage("Missing visibility information for this map!");
    		return;
    	}*/

    	// ********** CODE FOR STEALER
    	if (stealer)
    	{
    		// Bot where damaged
        	//senses.isShot();
        	
        	// Bot is hearing noise
        	//senses.isHearingNoise();
        	
        	if (shooting())
        	{
        		// TODO - bot is shooting ...
        		// turn around and shoot to nearest enemy
        		return;
        	}
        	else
        	{
        		// TODO - bot is not shooting ...
        	}
    		
        	if (ctf.isEnemyFlagHome())
        	{
        		runForFlag();
        	}
        	else if (ctf.getEnemyFlag().getHolder() == info.getId())
        	{
        		returnHome();
        	}
        	else if(ctf.isBotCarryingEnemyFlag())
        	{
        		// TODO - after communication run to BOT with FALG
        	}
        	else
        	{
        		// TODO - don't know what happen
        	}
        	
        	navigate(targetNavPoint);
    	}
    	// ********** CODE FOR DEFENDER
    	/*else
    	{
    		if (ctf.isOurFlagHome())
    		{
    			pickupSomeWeapon();
    			combatDefender(ctf.getOurBase());
    		}
    		else if (ctf.isOurFlagHeld() || ctf.isOurFlagDropped())
    		{
    			combatDefender(ctf.getEnemyBase());
    		}
    		else
    		{
    			// TODO - don't know what happen
    		}
    		
    		navigate(targetNavPoint);
    	}*/
 
    	navigation.navigate(targetNavPoint);
    	info.atLocation(targetNavPoint, 30);
    	missingWeapons = filterNotLoaded(requiredWeapons);
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
					move.turnTo(players.getNearestVisibleEnemy());
					shoot.shoot(weaponPrefs, players.getNearestVisibleEnemy());
					navigation.navigate(targetNavPoint);	
				}
			}
			else
			{
				// Another weapon shooting
				move.setWalk();
				navigation.setFocus(players.getNearestVisibleEnemy());
				shoot.shoot(weaponPrefs, players.getNearestVisibleEnemy());
			}
			
			return true;
    	}

    	if (!players.canSeeEnemies() && info.isShooting())
    	{
    		shoot.stopShooting();
    		move.setRun();
    		
    		return false;
    	}
    	
    	return false;
	}
	
    private boolean pickUpItemViaDistance(Collection<UT2004ItemType> requiredWeaponsColl)
    {
    	if (requiredWeapons == null)
    	{
    		return false;
    	}

    	double distance = Double.MAX_VALUE;
    	Item item = info.getNearestVisibleItem();
    	
    	if (item != null && item.getNavPoint() != null)
    	{
    		distance = info.getDistance(item.getNavPoint().getLocation());
    	}
    	  	
    	if (distance < DISTANCE_PICKU_UP_ITEM_FOR_FLAGSTEALER)
    	{
    		log.info("JSEM BLIZKO NEJAKEHO ITEMU!!! - distance: " + distance + " " + info.getNearestVisibleItem().getType().getName());
    		
    		if (item.getType().equals(senses.getItemPickedUp().getType()))
    		{
    			log.info("Picking up some WEAPON!");
    			navigate(ctf.getEnemyBase());
    		}
    		else
    		{
    			navigate(info.getNearestVisibleItem());
    		}
    		
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
    
    private void runForFlag()
    {
    	// Combat via STEALER
    	combatStealer();
    	
    	// Search ITEMs in BOT's near distance
    	if (pickUpItemViaDistance(missingWeapons))
    	{
    		return;
    	}
    	
    	// BOT needs urgent pick up health
    	if (needHealthUrgent())
    	{
    		if (pickupNearestHealth())
    		{
    			return;
    		}
    	}
    	
    	// Navigation to enemy base for FLAG
    	navigate(ctf.getEnemyBase());
    }
    
    private boolean returnHome()
    {
    	combatStealer();
    	navigate(ctf.getOurBase());
    	
        return true;
    }
    
    private void navigate(NavPoint target)
    {
    	/*runningToNavPoint = target;
        if (useCoverPath)
        {
            navigateCoverPath(target);
        }
        else
        {
            navigateStandard(target);
        }*/
        
        navigate(target.getLocation());
    }
    
    private void navigate(Location location)
    {
    	targetNavPoint = location;
    }
    
    private void navigate(Item item)
    {
    	navigate(item.getLocation());
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
    
    private void say(String string) {
        body.getCommunication().sendGlobalTextMessage(string);
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
