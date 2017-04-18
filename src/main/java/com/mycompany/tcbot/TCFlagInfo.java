package com.mycompany.tcbot;

import cz.cuni.amis.pogamut.unreal.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.teamcomm.mina.messages.TCMessageData;
import cz.cuni.amis.utils.token.IToken;
import cz.cuni.amis.utils.token.Tokens;

public class TCFlagInfo extends TCMessageData {

	private static final long serialVersionUID = 7866304634357691232L;

	public static final IToken MESSAGE_TYPE = Tokens.get("TCHello");

	private String flag;
	
	private UnrealId who;
	
	public TCFlagInfo(UnrealId who, String flag) {
		super(MESSAGE_TYPE);
		this.who = who;
		this.flag = flag;
	}
	
	// TODO - stahnout z pofamut workshop stranek
	
}
