package core;

import java.net.InetSocketAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Message_Resources.StatusMessage;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.util.ByteString;
import app_main.Link;
import core.serial.SSLTcpActor;

public class StatusMessageSender extends AbstractActor {
	private final static Logger log = LogManager.getLogger(StatusMessageSender.class);
	private final ActorRef statusMessageSender;
	private final String statusMessageDetails;
	private StatusMessageSender(InetSocketAddress statusMessageIp) {
		if(statusMessageIp!=null){
			this.statusMessageSender = getContext().actorOf(SSLTcpActor.props(statusMessageIp, false),"statusMessageTCP");
			this.statusMessageDetails = statusMessageIp.getHostString()+":"+statusMessageIp.getPort();
		}else{
		log.info("using same channel for StatusMessages and Receipt");
		this.statusMessageSender = null;
		this.statusMessageDetails =  null;
		}
		
	}
	@Override
	public void preStart() throws Exception {
		log.trace("starting StatusMessageSender Actor");
	}
	public static Props props(InetSocketAddress statusMessageIp){
		return Props.create(StatusMessageSender.class, statusMessageIp);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(String.class, sMsg->{
					if(Link.wait4CardRemoval){
					    if(sMsg.contains("CARD REMOVED")){
					        Link.cardRemoved = true;
					        log.info("card removed......");
					    }
					}
					if(statusMessageDetails==null){
						log.info("sending status message :"+sMsg);
						/**sending out status message **/
						getContext().getParent().tell(new StatusMessage(sMsg), getSelf());
					}else{
						log.info("sending status message to :" + statusMessageDetails);
						statusMessageSender.tell(ByteString.fromString(sMsg), getSelf());
					}
				})
				.build();
	}
	
	@Override
	public void postStop() throws Exception {
		log.trace("Stopping StatusMessageSender Actor");
	}

}
