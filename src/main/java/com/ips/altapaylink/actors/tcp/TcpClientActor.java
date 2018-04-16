package com.ips.altapaylink.actors.tcp;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.*;

import com.ips.altapaylink.actormessages.FailedAttempt;
import com.ips.altapaylink.actormessages.Protocol37Format;
import com.ips.altapaylink.actors.convertor.Link;
import com.ips.altapaylink.actors.protocol37.Protocol37ReadWriteHandler;
import com.ips.altapaylink.protocol37.Protocol37UnformattedMessage;

import akka.actor.*;
import akka.io.Tcp;
import akka.io.Tcp.*;
import akka.io.TcpMessage;
import akka.util.ByteString;

public class TcpClientActor extends AbstractActor {
	private final static Logger log = LogManager.getLogger(TcpClientActor.class);
    private final ActorRef tcpActor;
    private  ActorRef handler;
    private final InetSocketAddress remote;
    private boolean ackReceived;
    private boolean sentApplicationMessage = false;
    private int retryCycle = 0;
    private int gtMessageRetryCycle = 0;
 
    public static Props props(ActorRef statusMessageListener, ActorRef receiptGenerator, InetSocketAddress remote , String clientIp) {
        return Props.create(TcpClientActor.class, statusMessageListener, receiptGenerator, remote, clientIp);
    }

    private TcpClientActor(ActorRef statusMessageListener, ActorRef receiptGenerator, InetSocketAddress terminalIPandPort, String clientIp) {
        this.remote = terminalIPandPort;
        this.tcpActor = Tcp.get(getContext().system()).manager();
        	log.trace(getSelf().path().name()+" starting TCP Client");
        	tcpActor.tell(TcpMessage.connect(remote), getSelf());
        	log.info(getSelf().path().name()+" starting handler");
            this.handler = getContext().actorOf(Protocol37ReadWriteHandler.props(statusMessageListener, receiptGenerator),"P37Handler-"+clientIp);
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
					Link.sendToTerminal = true;
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
	            	   if(msg.data().utf8String().equalsIgnoreCase(Protocol37UnformattedMessage.ACK())){
	            		   log.info(getSelf().path().name()+" ACK");
	            		   log.info(getSelf().path().name()+" setting ackReceived to TRUE as ACK received ");
	            		   ackReceived = true;// stating that msg can be sent now as ack has been received for last sent msg
	            	   }
	            	   else if(msg.data().utf8String().equalsIgnoreCase(Protocol37UnformattedMessage.NACK())){
	            		   log.info(getSelf().path().name()+" NACK");
	            	   }
	            	  handler.tell(msg.data().utf8String(),getSelf());
	               }).match(String.class, s->{
	            	   log.info(getSelf().path().name()+" String: "+s);
	               }).match(ConnectionClosed.class, closed->{
	            	   log.trace(getSelf().path().name()+" connectin cLOSED with ped: "+closed);
						
	               }).match(CommandFailed.class, conn->{
						log.fatal(getSelf().path().name()+" connectin Failed with ped: "+conn);
						getContext().getParent().tell(new FailedAttempt("{\"errorCode\":\"05\",\"errorText\":\"Error -> CONNECTION WITH PED FAILED\"}"), getSelf());
						getContext().stop(getContext().parent());
						
					})
	               .build();
	    }
	 @Override
	public void postStop() throws Exception {
		log.info(getSelf().path().name()+" Stopping TCP client Actor");
	}
		
}