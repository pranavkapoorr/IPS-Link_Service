package com.ips.ipslink.actormessages;

public class FinalReceipt{
	private final String message;
	public FinalReceipt(String receipt) {
		this.message = receipt;
	}
	public String getReceipt(){
		return this.message;
	}
	
}