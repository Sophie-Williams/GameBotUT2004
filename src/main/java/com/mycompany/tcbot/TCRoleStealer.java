package com.mycompany.tcbot;

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
	
	@Override
	public String toString()
	{
		return msg;
	}
}
