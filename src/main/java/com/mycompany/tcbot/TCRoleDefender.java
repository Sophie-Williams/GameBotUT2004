package com.mycompany.tcbot;

import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TCRoleDefender extends TCMessageData
{
	/** Auto-generated */
	private static final long serialVersionUID = 5977590768004257177L;
	/** Message type */
	public static final IToken MESSAGE_TYPE = Tokens.get("Defender");
	/** Send / reciev message */
	private String msg;
	/** Unreal ID of sender */
	private UnrealId who;
	/** Our stealed flag location */
	private Location ourFlagLocation;
	/** Bot ID */
	private int ID;
	/** Bot location */
	private Location currentLocation;
	/** Nearest enemy bot location */
	private Location nearestEnemyBotLocation;
	
	public TCRoleDefender(UnrealId who, String msg)
	{
		super(MESSAGE_TYPE);
		this.who = who;
		this.msg = msg;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public UnrealId getWho() {
		return who;
	}

	public void setWho(UnrealId who) {
		this.who = who;
	}	
	
	public Location getOurFlagLocation() {
		return ourFlagLocation;
	}

	public void setOurFlagLocation(Location ouFlagLocation) {
		this.ourFlagLocation = ouFlagLocation;
	}
	
	public int getID() {
		return ID;
	}

	public void setID(int iD) {
		ID = iD;
	}

	public ILocated getCurrentLocation() {
		return currentLocation;
	}

	public void setCurrentLocation(Location currentLocation) {
		this.currentLocation = currentLocation;
	}

	public Location getNearestEnemyBotLocation() {
		return nearestEnemyBotLocation;
	}

	public void setNearestEnemyBotLocation(Location nearestEnemyBotLocation) {
		this.nearestEnemyBotLocation = nearestEnemyBotLocation;
	}

	@Override
	public String toString()
	{
		return msg;
	}
}
