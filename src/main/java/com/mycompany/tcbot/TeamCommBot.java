package com.mycompany.tcbot;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import cz.cuni.amis.pogamut.base.communication.translator.event.IWorldChangeEvent;
import cz.cuni.amis.pogamut.base.communication.worldview.event.IWorldEvent;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.AgentInfo;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UT2004ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ConfigChange;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GameInfo;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.InitedMessage;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;
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

	private static String[] names = new String[]{"Peter", "James", "Johnny", "Craig", "Jimmy", "Steve", "Ronnie", "Bobby"};
	
	static {
		List<String> n = MyCollections.toList(names);
		Collections.shuffle(n);
		names = n.toArray(new String[n.size()]);
	}
	
	/**
	 * Just for the numbering of bots.
	 */
	private static int number = 0;
	
	private int myNumber;
	
    @Override
    public Initialize getInitializeCommand() {
    	myNumber = ++number;
        return new Initialize().setName(names[(myNumber) % names.length] + (myNumber < 3 ? "-RED" : "-BLUE")).setTeam(myNumber < 3 ? AgentInfo.TEAM_RED : AgentInfo.TEAM_BLUE);
    }

    @Override
    public void botInitialized(GameInfo gameInfo, ConfigChange config, InitedMessage init) {
    	bot.getLogger().getCategory("Yylex").setLevel(Level.OFF);
        tcClient.getLog().setLevel(Level.ALL);    	
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
    public void logic() throws PogamutException {
    	
    	log.info("Player ID: " + myNumber);
    	boolean stealer = myNumber % 2 == 0 ? true : false;
    	
    	if (stealer)
    	{
    		// Pick some weapons
    		// TODO (via distance)
    		
    		// Steal enemy's flag
        	if (!haveFlag())
        	{
        		return;
        	}
        	
        	// Take enemy's flag home
        	// TODO
    	}
    	else
    	{
    		// Pick some weapons
    		// TODO (via distance)
    		
    		// Combat
    		// TODO - moving in base
    		// TODO - add control if is flag in base
        	if (combatWithoutFlag())
        	{
        		return;
            }
    	}
 
    	// If I don't need it - DELETE
        pickUpItems();
    }
    
    private boolean haveFlag()
    {
    	navigation.navigate(this.ctf.getEnemyBase());

    	if (this.ctf.getEnemyFlag().getState().equals("home"))
    	{
    		return false;
    	}
    	
    	return true;
    }
    
    private boolean combatWithoutFlag()
    {
    	if (players.canSeeEnemies())
    	{
    		// INFO
            bot.getBotName().setInfo("CMB");
            bot.getBotName().deleteInfo("To");
            
            // navigation to nearest visible enemy
            navigation.navigate(players.getNearestVisibleEnemy());
            // shooting on nearest visible enemy
            shoot.shoot(players.getNearestVisibleEnemy());
            
            return true;
    	}
    	else if (!players.canSeeEnemies() && info.isShooting())
    	{
    		shoot.stopShooting();
    		
    		return false;
    	}
    	else
    	{
    		return false;
    	}
    }
    
    private boolean pickUpItems()
    {
    	// INFO
        bot.getBotName().setInfo("PUI");
        
        // if need HEALTH - pick up it
        if (needHealthUrgent())
        {
        	if (pickupNearestHealth()) return true;        	
        }
        
        // if need WEAPON - pick up it
        if (pickupSomeWeapon())
        {
        	return true;
        }
        
        // if need GOOD WEAPON - pick up it
        if (pickupGoodItem())
        {
        	return true;
        }
        
        // pick up RANDOM WEAPON
        pickupRandomItem();
        
        return true;
    }
    
    private boolean needHealthUrgent() {
        return info.getHealth() < 20 || (info.getHealth() + info.getArmor()) < 40;
    }
    
    private boolean pickupSomeWeapon() {
        if (!weaponry.hasLoadedWeapon(UT2004ItemType.SHOCK_RIFLE)) {
            if (navigateTo(UT2004ItemType.SHOCK_RIFLE)) return true;
        }
        if (!weaponry.hasLoadedWeapon(UT2004ItemType.MINIGUN)) {
            if (navigateTo(UT2004ItemType.MINIGUN)) return true;
        }
        if (!weaponry.hasLoadedWeapon(UT2004ItemType.LINK_GUN)) {
            if (navigateTo(UT2004ItemType.LINK_GUN)) return true;
        }
        return false;
    }
    
    private boolean pickupGoodItem() {
        if (items.getSpawnedItems(UT2004ItemType.SHIELD_PACK).size() > 0) {
            if (navigateTo(UT2004ItemType.SHIELD_PACK)) return true;
        }
        if (info.getHealth() < 80) {
            if (navigateTo(UT2004ItemType.HEALTH_PACK)) return true;
        }
        return false;
    }
    
    private boolean pickupNearestHealth() {
        if (navigateTo(UT2004ItemType.HEALTH_PACK)) return true;
        return false;
    }
    
    private boolean pickupRandomItem() {
        navigateWeapon();
        return true;
    }
    
    private boolean navigateTo(UT2004ItemType type) {
        if (navigation.isNavigatingToItem() && navigation.getCurrentTargetItem().getType() == type) return true;
        Item item = fwMap.getNearestItem(items.getSpawnedItems(type).values(), navPoints.getNearestNavPoint());        
        if (item == null) {
            log.warning("No " + type.getName() + " to run to...");
            return false;
        }
        if (item.getLocation() == null) {
            log.warning("No location " + type.getName() + " to run to...");
            return false;
        }
        navigation.navigate(item);
        bot.getBotName().setInfo("To", item.getType().getName());
        say("To: " + item.getType().getName());
        return true;
    }
    
    private void navigateWeapon() {
        if (navigation.isNavigatingToItem() && navigation.getCurrentTargetItem().getType().getCategory() == ItemType.Category.WEAPON) return;
        Item item = MyCollections.getRandom(items.getSpawnedItems(ItemType.Category.WEAPON).values());        
        if (item == null) {
            log.warning("No weapon to run to...");
            return;
        }
        navigation.navigate(item);
        bot.getBotName().setInfo("To", item.getType().getName());
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
        new UT2004BotRunner(TeamCommBot.class, "TCBot").setMain(true).setLogLevel(Level.WARNING).startAgents(3);       
    }
}
