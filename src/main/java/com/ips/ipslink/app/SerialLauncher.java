package com.ips.ipslink.app;

import com.ips.ipslink.actormessages.Protocol37Format;
import com.ips.ipslink.actors.serial.SerialManager;
import com.ips.ipslink.protocol37.Protocol37UnformattedMessage;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class SerialLauncher {
	public static void main(String[] args){
		ActorSystem system = ActorSystem.create("SerialSystem");
		ActorRef serial = system.actorOf(SerialManager.props("COM4"));
		serial.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(1)), ActorRef.noSender());
		serial.tell(new Protocol37Format(Protocol37UnformattedMessage.payment(0, 10200)), ActorRef.noSender());
		//serial.tell(new Protocol37Format(Protocol37UnformattedMessage.reversal(0)), ActorRef.noSender());
		//serial.tell(new Protocol37Format(Protocol37UnformattedMessage.dllFunction(1)), ActorRef.noSender());
		
	}
}
