package com.mycompany.tcbot;

import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TCRoleStealer extends TCMessageData
{
	/** Auto-generated */
	private static final long serialVersionUID = -1212205253254994018L;
	/** Message type */
	public static final IToken MESSAGE_TYPE = Tokens.get("Stealer");
	/** Send / reciev message */
	private String msg;
	/** Unreal ID of sender */
	private UnrealId who;
	/** Enemy flag location */
	private Location enemyFlagLocation;
	/** Our stealed flag location */
	private Location ourFlagLocation;
	/** Bot ID */
	private int ID;
	/** Bot location */
	private Location currentLocation;
	/** Nearest enemy bot location */
	private Location nearestEnemyBotLocation;
	
	public TCRoleStealer(UnrealId who, String msg)
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
	
	public Location getEnemyFlagLocation() {
		return enemyFlagLocation;
	}

	public void setEnemyFlagLocation(Location enemyFlagLocation) {
		this.enemyFlagLocation = enemyFlagLocation;
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

	public Location getOurFlagLocation() {
		return ourFlagLocation;
	}

	public void setOurFlagLocation(Location ouFlagLocation) {
		this.ourFlagLocation = ouFlagLocation;
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
