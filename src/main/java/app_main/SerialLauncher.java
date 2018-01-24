package app_main;

import Message_Resources.Protocol37Format;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import core.serial.SerialManager;
import protocol37.Protocol37UnformattedMessage;

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
