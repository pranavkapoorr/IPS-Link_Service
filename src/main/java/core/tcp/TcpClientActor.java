package core.tcp;

import java.net.InetSocketAddress;
import org.apache.logging.log4j.*;
import Message_Resources.FailedAttempt;
import Message_Resources.Protocol37Format;
import akka.actor.*;
import akka.io.Tcp;
import akka.io.Tcp.*;
import akka.io.TcpMessage;
import akka.util.ByteString;
import app_main.Link;
import protocol37.*;

public class TcpClientActor extends AbstractActor {
	private final static Logger log = LogManager.getLogger(TcpClientActor.class);
    private final ActorRef tcpActor;
    private  ActorRef handler;
    private final InetSocketAddress remote;
    private boolean ackReceived;
    private boolean sentApplicationMessage = false;
    private int retryCycle = 0;
    private int gtMessageRetryCycle = 0;
 
    public static Props props(ActorRef statusMessageListener, ActorRef receiptGenerator, InetSocketAddress remote ) {
        return Props.create(TcpClientActor.class, statusMessageListener, receiptGenerator, remote);
    }

    private TcpClientActor(ActorRef statusMessageListener, ActorRef receiptGenerator, InetSocketAddress terminalIPandPort) {
        this.remote = terminalIPandPort;
        this.tcpActor = Tcp.get(getContext().system()).manager();
        	log.trace("starting TCP Client");
        	tcpActor.tell(TcpMessage.connect(remote), getSelf());
        	log.info("starting handler");
            this.handler = getContext().actorOf(Protocol37ReadWriteHandler.props(statusMessageListener, receiptGenerator));
      }
    
    @Override
    public void preStart() throws Exception {
    	log.info("Starting TCP client Actor");
    }

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(CommandFailed.class, conn->{
					log.fatal("connectin Failed:"+conn);
					getContext().getParent().tell(new FailedAttempt("{\"errorText\":\"Error ->*****CHECK TERMINAL CONNECTIVITY AND ADDRESS*****\"}"), getSelf());
					getContext().stop(getSelf());
					//getContext().stop(getContext().getParent());
				})
				.match(Connected.class, conn->{
					Link.sendToTerminal = true;
					log.info("connected :"+conn);
					 ackReceived = true;
			         log.info("ackReceived set to allow first message to be sent through tcp");
		            getSender().tell(TcpMessage.register(getSelf()), getSelf());
		            getContext().become(connected(getSender()));
		            
				}).build();
	}
	 private Receive connected(final ActorRef sender) {
	        return receiveBuilder()
	        		.match(Protocol37Format.class, msg->{
	        		//	log.info("in tcp now : "+ msg.getMessageToSend() );
	        			//converts message to byteString
	        			ByteString string= ByteString.fromString(msg.getFormattedMessageToSend());
	        			//forward if message is ACK to be sent
	        			if(msg.getFormattedMessageToSend().equalsIgnoreCase(Protocol37UnformattedMessage.ACK())){
	        				sender.tell(TcpMessage.write(string), getSelf());
	        				log.info("sent ack: " + string.utf8String());
		        		 }else{
		        			 //other than ACK
		        			 if(!msg.getMessageToSend().contains("0E")){ //if apart from E command
		        				 if(msg.getMessageToSend().contains("0U")){
		        					/*****ADDITION GT MESSAGE "U" BLOCK*****/
		        					 /**U message will be sent only when application message is sent and its ack is received**/
			        						if(sentApplicationMessage && ackReceived){ 
			        							sender.tell(TcpMessage.write(string), getSelf());
			        							log.info("sent U msg: at cycle: "+gtMessageRetryCycle +" -> "+ string.utf8String());
			        						}else{
			        							if(gtMessageRetryCycle == 0){ //log only shows for 1st cycle
			        								log.error("Application Message isn't sent yet : "+msg.getFormattedMessageToSend());
					        						log.debug("retrying to send U msg");
					        					}else{
					        						
					        					}
			        							getSelf().tell(msg,getSelf());
			        							gtMessageRetryCycle++;
			        						}
				        				
		        				 }else{ 
		        					 	/****Application Message Block (A,S,T,D,...etc)****/
		        					 	if(ackReceived){// IF ACK is received for previously sent message
				        					sender.tell(TcpMessage.write(string), getSelf());
			        						log.info("sent Application msg: at cycle: "+retryCycle +" -> "+ string.utf8String());
			        						retryCycle = 0;
			        						sentApplicationMessage =  true;
			        						ackReceived =  false;
				        					}else{
				        						//if ack is not received for previously sent message then wait
					        					if(retryCycle == 0){//if cycle is 1 then log it (reduces log)
					        						log.error("havent received ack for last msg sent so cannot send: "+msg.getFormattedMessageToSend());
					        						log.debug("retrying to send same msg");
					        					}
					        					else if(retryCycle > 100000000){
					        						log.fatal("TIMEOUT WAITING ACK");
					        						getContext().getParent().tell(new FailedAttempt("{\"errorText\":\"Error ->*****CHECK PED CONNECTIVITY*****\"}"), getSelf());
					        						getContext().stop(getContext().parent());
					        					}
					        					else{	
						        					//	log.debug("retry Cycle -> "+retryCycle);
						        						//TimeUnit.MILLISECONDS.sleep(60); //ESTIMATED TIME FOR RECEIVING ACK
						        					}
					        					//sending same message to itself unless ack is received
					        					getSelf().tell(msg,getSelf());
					        					retryCycle++;
				        					}	
		        				 }
		        			}else{
		        					sender.tell(TcpMessage.write(string), getSelf());
		        					log.info("sent E msg: " + string.utf8String());
		        					log.info("setting ackReceived to false in order to wait for ack before next msg is sent");
		        					ackReceived = false;
				        		}
				        		
		        		 }
	        			})
	               .match(Received.class, msg->{
	            	  // String message = msg.data().utf8String();
	            	//   log.info("received-tcp: "+ message);
	            	   if(msg.data().utf8String().equalsIgnoreCase(Protocol37UnformattedMessage.ACK())){
	            		   log.info("ACK");
	            		   log.info("setting ackReceived to TRUE as ACK received ");
	            		   ackReceived = true;// stating that msg can be sent now as ack has been received for last sent msg
	            	   }
	            	   else if(msg.data().utf8String().equalsIgnoreCase(Protocol37UnformattedMessage.NACK())){
	            		   log.info("NACK");
	            	   }
	            	  handler.tell(msg.data().utf8String(),getSelf());
	               }).match(String.class, s->{
	            	   log.info("String: "+s);
	               }).match(ConnectionClosed.class, closed->{
	            	   log.fatal("connectin cLOSED:"+closed);
	            	   getContext().getParent().tell(new FailedAttempt("{\"errorText\":\"Error ->*****CONNECTION CLOSED BY TERMINAL*****\"}"), getSelf());
	            	  // getContext().stop(context().parent());
						
	               }).match(CommandFailed.class, conn->{
						log.fatal("connectin Failed:"+conn);
						getContext().getParent().tell(new FailedAttempt("{\"errorText\":\"Error ->*****UNSUCCESSFUL*****\"}"), getSelf());
						getContext().stop(getContext().parent());
						
					})
	               .build();
	    }
	 @Override
	public void postStop() throws Exception {
		log.info("Stopping TCP client Actor");
	}
		
}



