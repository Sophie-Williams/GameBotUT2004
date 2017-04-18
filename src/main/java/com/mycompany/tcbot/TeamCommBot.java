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
import cz.cuni.amis.utils.future.FutureStatus;
import cz.cuni.amis.utils.future.FutureWithListeners;
import cz.cuni.amis.utils.future.IFutureListener;

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
    	// FIRST COLLECT REQUIRED WEAPONS
    	UT2004ItemType[] requiredWeapons = new UT2004ItemType[] { UT2004ItemType.LIGHTNING_GUN, UT2004ItemType.MINIGUN, UT2004ItemType.FLAK_CANNON };
    	
    	if (existsInMap(requiredWeapons)) { 
    		return;
    	}  
    	
    	if (!hasLoaded(requiredWeapons)) {
    		Set<UT2004ItemType> missingWeapons = filterNotLoaded(requiredWeapons);
    		collectWeapons(missingWeapons);
    		return;
    	} 
   	
    	if (!existsInMap(requiredWeapons))
        {
            return;
        }
    	
    	if (!hasLoaded(requiredWeapons))
        {
            Set<UT2004ItemType> missingWeapons = filterNotLoaded(requiredWeapons);
         
            if (info.isShooting())
            {
                shoot.stopShooting();
            }
        
            if (collectWeapons(missingWeapons))
            {
                return;
            }
        }
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
     * Displays info tag on the bot.
     * @param goal
     */
    private void debugGoal(String goal) {
    	bot.getBotName().setInfo(goal);
    }
    
    /**
	 * BEHAVIOR - try to collect 'requiredWeapons'; start with the nearest first.
	 * @param requiredWeapons
	 */
    private boolean collectWeapons(Collection<UT2004ItemType> requiredWeapons) {
    	Set<Item> nearest = getNearestSpawnedItems(requiredWeapons);
    	
    	Item target = DistanceUtils.getNearest(nearest, info.getNearestNavPoint(), 
				new DistanceUtils.IGetDistance<Item>() {
					@Override
					public double getDistance(final Item object, ILocated target) {
						return fwMap.getDistance(object.getNavPoint(), info.getNearestNavPoint());
					}			
		});
		if (target == null) {
			log.severe("No item to navigate to! requiredWeapons.size() = " + requiredWeapons.size());
			return false;
		}
		debugGoal("C:" + target.getType().getName() + " | " + (int)(fwMap.getDistance(target.getNavPoint(), info.getNearestNavPoint())));
		
		navigation.navigate(target);
		
		return true;
	}
    
    /**
	 * Returns weapons that the bot does not have or are not loaded.
	 * @param requiredWeapons
	 * @return
	 */
	private Set<UT2004ItemType> filterNotLoaded(UT2004ItemType[] requiredWeapons) {
		Set<UT2004ItemType> result = new HashSet<UT2004ItemType>();
		for (UT2004ItemType weapon : requiredWeapons) {
    		if (!weaponry.hasPrimaryLoadedWeapon(weapon)) result.add(weapon);
    	}
		return result;
	}
    
    /**
     * Checks if all 'requiredWeapons' are loaded...
     * @param requiredWeapons
     * @return
     */
	private boolean hasLoaded(UT2004ItemType[] requiredWeapons) {
    	for (UT2004ItemType weapon : requiredWeapons) {
    		if (!weaponry.hasPrimaryLoadedWeapon(weapon)) return false;
    	}
		return true;
	}
    
    /**
     * Checks whether some items exists within the map ...
     * @param requiredItems
     * @return
     */
    private boolean existsInMap(UT2004ItemType[] requiredItems) {
    	for (UT2004ItemType item : requiredItems) {
    		if (items.getAllItems(item).size() == 0) {
    			log.severe("Map " + game.getMapName() + " does not have any items of: " + item.getName());
    			return false;
    		}    		
    	}
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
        new UT2004BotRunner(TeamCommBot.class, "TCBot").setMain(true).setLogLevel(Level.WARNING).startAgents(3);       
    }
}
