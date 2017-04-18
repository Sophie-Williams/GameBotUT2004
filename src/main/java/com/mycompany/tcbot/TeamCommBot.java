package com.mycompany.tcbot;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import cz.cuni.amis.pogamut.base.communication.translator.event.IWorldChangeEvent;
import cz.cuni.amis.pogamut.base.communication.worldview.event.IWorldEvent;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensor.AgentInfo;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.ConfigChange;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.GameInfo;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.InitedMessage;
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
    public void logic() {
    	if (!tcClient.isConnected()) {
    		msgNum = 0;
    		myChannelId = -1;
    		return;
    	}
    	
    	// YOU CAN CHECK FOR ALL NEW MESSAGES IN THE LOGIC AS WELL
    	for (TCMessage msg : tcClient.getMessages()) {
    		log.info("InLogic: " + toString(msg));
    	}
    	
    	if (myNumber != 1) return;
    	
    	if (tcClient.getConnectedAllBots().size() != 3) {
    		// wait till all bots get connected...
    		return;
    	}
    	
    	// WE WILL SEND MESSAGE EVERY 3 SECONDS (not to overload the console output...)
    	if (!msgCooldown.tryUse()) return;
    	
    	++msgNum;
    	
    	switch(msgNum) {
    	case 1:
    		log.info("Sending to ALL...");
    		tcClient.sendToAll(new TCHello(info.getId(), "Hyo ALL!"));
    		tcClient.sendToAllOthers(new TCHello(info.getId(), "Hyo ALL OTHERS!"));
    		return;
    	
    	case 2:
    		log.info("Sending to TEAM...");
    		tcClient.sendToTeam(new TCHello(info.getId(), "Hyo TEAM!"));
    		tcClient.sendToTeamOthers(new TCHello(info.getId(), "Hyo others in the TEAM!"));
    		return;
    		
    	case 3:    		
    		Set<UnrealId> connected = new HashSet<UnrealId>(tcClient.getConnectedAllBots());
    		connected.remove(info.getId());
    		UnrealId sendTo = MyCollections.getRandom(connected);
    		if (sendTo != null) {
    			log.info("Sending to BOT " + sendTo.getStringId() + "...");
    			tcClient.sendToBot(sendTo, new TCHello(info.getId(), "Private ears only!"));
    		}
    		return;
    		
    	case 4:
    		log.info("Creating channel...");
    		tcClient.requestCreateChannel().addFutureListener(new IFutureListener<TCInfoTeamChannelCreated>() {				
				@Override
				public void futureEvent(FutureWithListeners<TCInfoTeamChannelCreated> source, FutureStatus oldStatus, FutureStatus newStatus) {
					if (newStatus == FutureStatus.FUTURE_IS_READY) {
						log.info("Channel " + source.get().getChannel().getChannelId() + " created...");
						myChannelId = source.get().getChannel().getChannelId();
					} else {
						log.warning("FAILED TO CREATE CHANNEL -> retrying...");
						myChannelId = -1;
						msgNum = 3;						
					}
				}
			});
    		return;
    		
    	case 5:
    		if (myChannelId < 0) {
    			--msgNum;
    			return;
    		}
    		if (tcClient.getChannel(myChannelId).getConnectedBots().size() <= 1) {
    			log.warning("There are too few bots in the channel...");
    			--msgNum;
    			return;
    		}
    		log.info("Sending to CHANNEL...");
    		tcClient.sendToChannel(myChannelId, new TCHello(info.getId(), "Channels are great!"));    		
    		return;
    		
    	case 6:
    		log.info("Destroying channel...");
    		if (tcClient.getChannel(myChannelId) == null) {
    			// LOGIC GOT HERE BEFORE FUTURE LISTENER
    			msgNum = 0;
    			return;
    		}
    		tcClient.requestDestroyChannel(myChannelId).addFutureListener(new IFutureListener<TCInfoTeamChannelDestroyed>() {				
				@Override
				public void futureEvent(FutureWithListeners<TCInfoTeamChannelDestroyed> source, FutureStatus oldStatus, FutureStatus newStatus) {
					if (newStatus == FutureStatus.FUTURE_IS_READY) {
						log.info("Channel destroyed...");
						myChannelId = -1;
						msgNum = 0;
					} else {
						log.info("FAILED TO DESTROY THE CHANNEL ...");
						if (tcClient.getChannel(myChannelId) == null) {
							log.info("Channel does not exist nevertheless...");
							myChannelId = -1;
							msgNum = 0;
						} else {
							log.info("Channel still exist, retrying...");
							msgNum = 5;
						}
					}
				}
			});
    		
    	case 8:
    		--msgNum;
    		return;
    		
    	default:
    		return;
    	}
    	
    	
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
