package com.ips.ipslink.actormessages;

public class FailedAttempt {
	private final String message;
	public FailedAttempt(String Message) {
		this.message = Message;
	}
	public String getMessage(){
		return this.message;
	}
	
}
