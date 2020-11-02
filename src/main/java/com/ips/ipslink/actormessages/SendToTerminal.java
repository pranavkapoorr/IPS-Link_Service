package com.ips.ipslink.actormessages;

public class SendToTerminal {
	private final boolean send;
	public SendToTerminal(boolean send) {
		this.send = send;
	}
	public boolean sendNow(){
		return this.send;
	}
}