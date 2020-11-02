package com.ips.ipslink.actors.tcp;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.*;

import com.ips.ipslink.actormessages.*;
import com.ips.ipslink.protocol37.Protocol37UnformattedMessage;

import akka.actor.*;
import akka.io.Tcp;
import akka.io.Tcp.*;
import akka.io.TcpMessage;
import akka.util.ByteString;
import scala.concurrent.duration.Duration;

public class TcpClientActor extends AbstractActor {
	private final static Logger log = LogManager.getLogger(TcpClientActor.class);
    private final ActorRef tcpActor;
    private final InetSocketAddress remote;
    private boolean ackReceived;
    private boolean sentApplicationMessage = false;
    private int retryCycle = 0;
    private int gtMessageRetryCycle = 0;
    private final String clientIp;
    private boolean wait4Reply;
    private boolean paxTrigger;
 
    public static Props props(InetSocketAddress remote , String clientIp) {
        return Props.create(TcpClientActor.class, remote, clientIp);
    }

    private TcpClientActor(InetSocketAddress terminalIPandPort, String clientIp) {
        this.remote = terminalIPandPort;
        this.clientIp = clientIp;
        this.tcpActor = Tcp.get(getContext().system()).manager();
        	log.trace(getSelf().path().name()+" starting TCP Client");
        	tcpActor.tell(TcpMessage.connect(remote), getSelf());
        	log.info(getSelf().path().name()+" starting handler");
        	 wait4Reply = false;
        	 paxTrigger = false;
        	//this.handler = getContext().actorSelection(ActorPath.fromString("akka://IPS-SYSTEM/user/SERVER/handler-"+clientIp+"/IPS-"+clientIp+"/p37Handler-"+clientIp)).anchor();
            //this.handler = getContext().actorOf(Protocol37ReadWriteHandler.props(statusMessageListener, receiptGenerator),"P37Handler-"+clientIp);
      }
    
    @Override
    public void preStart() throws Exception {
    	log.info(getSelf().path().name()+" Starting TCP client Actor");
    }

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(CommandFailed.class, conn->{
					log.fatal(getSelf().path().name()+" connectin Failed:"+conn);
					getContext().getParent().tell(new FailedAttempt("{\"errorCode\":\"03\",\"errorText\":\"Error -> CHECK PED CONNECTIVITY AND ADDRESS\"}"), getSelf());
					getContext().stop(getSelf());
					//getContext().stop(getContext().getParent());
				})
				.match(Connected.class, conn->{
					getContext().getParent().tell(new SendToTerminal(true), getSelf());//ready to send message as connected to terminal
					log.info(getSelf().path().name()+" connected to ped: "+conn);
					 ackReceived = true;
			         log.info(getSelf().path().name()+" ackReceived set to allow first message to be sent through tcp");
		            getSender().tell(TcpMessage.register(getSelf()), getSelf());
		            getContext().become(connected(getSender()));
		            
				}).build();
	}
	 private Receive connected(final ActorRef sender) {
	        return receiveBuilder()
	        		.match(Protocol37Format.class, msg->{
	        			//converts message to byteString
	        			ByteString string= ByteString.fromString(msg.getFormattedMessageToSend());
	        			//forward if message is ACK to be sent
	        			if(msg.getFormattedMessageToSend().equalsIgnoreCase(Protocol37UnformattedMessage.ACK())){
	        				sender.tell(TcpMessage.write(string), getSelf());
	        				log.info(getSelf().path().name()+" sent ack: " + string.utf8String());
	        				int timer = paxTrigger?5:60;
	        				getContext().setReceiveTimeout(Duration.create(timer, TimeUnit.SECONDS));//waiting for reply for 90 seconds.
        					wait4Reply = true;//setting waiting to true
        					log.info(getSelf().path().name()+" waiting for "+ timer +" seconds for AppMsg and enable wait4Reply.");
		        		 }else{
		        			 //other than ACK
		        			 if(!msg.getMessageToSend().contains("0E")){ //if apart from E command
		        				 if(msg.getMessageToSend().contains("0U")){
		        					/*****ADDITION GT MESSAGE "U" BLOCK*****/
		        					 /**U message will be sent only when application message is sent and its ack is received**/
			        						if(sentApplicationMessage && ackReceived){ 
			        							sender.tell(TcpMessage.write(string), getSelf());
			        							log.info(getSelf().path().name()+" sent U msg: at cycle: "+gtMessageRetryCycle +" -> "+ string.utf8String());
			        						}else{
			        							if(gtMessageRetryCycle == 0){ //log only shows for 1st cycle
			        								log.debug(getSelf().path().name()+" Application Message isn't sent yet : "+msg.getFormattedMessageToSend());
					        						log.debug(getSelf().path().name()+" retrying to send U msg");
					        					}else{
					        						
					        					}
			        							TimeUnit.NANOSECONDS.sleep(1);
			        							getSelf().tell(msg,getSelf());
			        							gtMessageRetryCycle++;
			        						}
				        				
		        				 }else{ 
		        					 	/****Application Message Block (A,S,T,D,...etc)****/
		        					 	if(ackReceived){// IF ACK is received for previously sent message
				        					sender.tell(TcpMessage.write(string), getSelf());
				        					getContext().setReceiveTimeout(Duration.create(5, TimeUnit.SECONDS));//waiting for reply for 90 seconds.
				        					wait4Reply = true;//setting waiting to true
				        					log.info(getSelf().path().name()+" waiting for 5 seconds for Ack/NAck and enable wait4Reply.");
			        						log.info(getSelf().path().name()+" sent Application msg: at cycle: "+retryCycle +" -> "+ string.utf8String());
			        						retryCycle = 0;
			        						sentApplicationMessage =  true;
			        						ackReceived =  false;
				        					}else{
				        						//if ack is not received for previously sent message then wait
					        					if(retryCycle == 0){//if cycle is 1 then log it (reduces log)
					        						log.error(getSelf().path().name()+" havent received ack for last msg sent so cannot send: "+msg.getFormattedMessageToSend());
					        						log.debug(getSelf().path().name()+" retrying to send same msg");
					        					}
					        					else if(retryCycle > 100000000){
					        						log.fatal(getSelf().path().name()+" TIMEOUT WAITING ACK");
					        						getContext().getParent().tell(new FailedAttempt("{\"errorCode\":\"04\",\"errorText\":\"Error -> CHECK PED CONNECTIVITY\"}"), getSelf());
					        						getContext().stop(getContext().parent());
					        					}
					        					//sending same message to itself unless ack is received
					        					getSelf().tell(msg,getSelf());
					        					retryCycle++;
				        					}	
		        				 }
		        			}else{
		        					sender.tell(TcpMessage.write(string), getSelf());
		        					log.info(getSelf().path().name()+" sent E msg: " + string.utf8String());
		        					log.info(getSelf().path().name()+" setting ackReceived to false in order to wait for ack before next msg is sent");
		        					ackReceived = false;
				        		}
				        		
		        		 }
	        			})
	               .match(Received.class, msg->{
	            	   /**to check if msg received in time**/
	            	   wait4Reply = false;
	            	   getContext().setReceiveTimeout(Duration.Undefined());
	            	   String receivedMsg = msg.data().utf8String();
	            	   log.info(getSelf().path().name()+" disabling wait4Reply as data received ");
	            	   if(receivedMsg.equalsIgnoreCase(Protocol37UnformattedMessage.ACK())){
	            		   log.info(getSelf().path().name()+" ACK");
	            		   log.info(getSelf().path().name()+" setting ackReceived to TRUE as ACK received ");
	            		   ackReceived = true;// stating that msg can be sent now as ack has been received for last sent msg
	            	   }
	            	   else if(receivedMsg.equalsIgnoreCase(Protocol37UnformattedMessage.NACK())){
	            		   log.warn(getSelf().path().name()+" NACK");
	            	   }else if(receivedMsg.contains("01Cancelled by user")||//****** for pax to reduce timer+*******////////////
	            			   receivedMsg.contains("01REVERSAL not allowed")||
	            			   receivedMsg.contains("010000000000000000000   00")||
	            			   receivedMsg.contains("01000000000000Terminal timeout")||
	            			   receivedMsg.contains("0D00000000")||
	            			   receivedMsg.contains("01Transaction interrupted")) {
	            		   paxTrigger = true;//to make sure next msg uses less timer
	            	   }
	            	   ActorRef handler = getContext().actorFor("../p37Handler-"+clientIp);
	            	   handler.tell(receivedMsg,getSelf());
	            	   //handler.tell(msg.data().utf8String(),getSelf());
	               }).match(String.class, s->{
	            	   log.info(getSelf().path().name()+" String: "+s);
	               }).match(ConnectionClosed.class, closed->{
	            	   log.error(getSelf().path().name()+" connectin cLOSED with ped: "+closed);
						
	               }).match(CommandFailed.class, conn->{
						log.fatal(getSelf().path().name()+" connectin Failed with ped: "+conn);
						getContext().getParent().tell(new FailedAttempt("{\"errorCode\":\"05\",\"errorText\":\"Error -> CONNECTION WITH PED FAILED\"}"), getSelf());
						getContext().stop(getContext().parent());
						
					})
	               .match(ReceiveTimeout.class, r -> {
	            	   if(wait4Reply == true) {
	            		   	wait4Reply = false;
	            		   	ActorSelection receiptActr = getContext().actorSelection("../receipt_Generator_Actor-"+clientIp);
	            		   	receiptActr.tell(new FailedAttempt("{\"errorCode\":\"10\",\"errorText\":\"Error -> No Data Received.\"}"),getSelf());//failed attempt sent to receiptgenerator
	            		   	log.trace(getSelf().path().name()+" turning off waiting for data and terminating.");
	   						getContext().setReceiveTimeout(Duration.Undefined());
	            	   }
	               })
	               .build();
	    }
	 @Override
	public void postStop() throws Exception {
		log.info(getSelf().path().name()+" Stopping TCP client Actor");
	}
		
}