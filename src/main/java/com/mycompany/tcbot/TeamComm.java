package com.mycompany.tcbot;

import cz.cuni.amis.pogamut.base3d.worldview.object.ILocated;
import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TeamComm extends TCMessageData
{
	/** Auto-generated */
	private static final long serialVersionUID = -708788479826875219L;
	/** Message type */
	public static final IToken MESSAGE_TYPE = Tokens.get("TeamComm");
	/** Send / reciev message */
	private String msg;
	/** Unreal ID of sender */
	private UnrealId who;
	/** Enemy flag location */
	private ILocated enemyFlagLocation;
	/** Bot location */
	private ILocated ourFlagLocation;
	/** Bot location */
	private ILocated currentLocation;
	
	public TeamComm(UnrealId who, String msg)
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

	public ILocated getEnemyFlagLocation() {
		return enemyFlagLocation;
	}

	public void setEnemyFlagLocation(ILocated enemyFlagLocation) {
		this.enemyFlagLocation = enemyFlagLocation;
	}

	public ILocated getOurFlagLocation() {
		return ourFlagLocation;
	}

	public void setOurFlagLocation(ILocated ourFlagLocation) {
		this.ourFlagLocation = ourFlagLocation;
	}

	public ILocated getCurrentLocation() {
		return currentLocation;
	}

	public void setCurrentLocation(ILocated currentLocation) {
		this.currentLocation = currentLocation;
	}
}
