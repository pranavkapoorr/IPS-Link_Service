package core.tcp;


import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.io.TcpMessage;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.ConnectionClosed;
import akka.io.Tcp.Received;
import akka.util.ByteString;
import app_main.IPS_Link;
import core.Database;
import scala.concurrent.duration.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;

import Message_Resources.FailedAttempt;
import Message_Resources.FinalReceipt;
import Message_Resources.IPS_Protocol;
import Message_Resources.StatusMessage;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;


public class TcpConnectionHandlerActor extends AbstractActor {

	private final static Logger log = LogManager.getLogger(TcpConnectionHandlerActor.class); 
	private final InetSocketAddress clientIP;
	private ActorRef sender;
	private ActorRef IPS;
	//private Database db;
	public TcpConnectionHandlerActor(InetSocketAddress clientIP) {
		this.clientIP = clientIP;
		//db = new Database();
	}

	public static Props props(InetSocketAddress clientIP) {
		return Props.create(TcpConnectionHandlerActor.class, clientIP);
	}
	
	@Override
	public void preStart() throws Exception {
		//db.insertConnection(LocalDateTime.now().toString(), clientIP.getHostString(), String.valueOf(clientIP.getPort()));
		//TcpServerActor.clientnum ++;
		//System.err.println(TcpServerActor.clientnum);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Received.class, msg->{
					sender = getSender();
					String messageX = msg.data().utf8String();
					log.info("received-tcp: "+ messageX);
					if(messageX.startsWith("{") && messageX.endsWith("}")){
						if(IPS==null||IPS.isTerminated()){
							ObjectMapper mapper = new ObjectMapper();
							String message = messageX.replaceAll("'", "\\\"");
							IPS_Protocol ips_message = mapper.readValue(message, IPS_Protocol.class);
							HashMap<String,String> resourceMap = ips_message.getParsedMap();
							if(isValidated(resourceMap)){
								log.trace("Incoming Json Validated..!");
								InetSocketAddress statusMessageAddress;
								InetSocketAddress terminalAddress;
								if(validIpAndPort(resourceMap.get("terminalIp"),resourceMap.get("terminalPort"))){		
									terminalAddress = new InetSocketAddress(resourceMap.get("terminalIp"),Integer.parseInt(resourceMap.get("terminalPort")));
									if(validIpAndPort(resourceMap.get("statusMessageIp"),resourceMap.get("statusMessagePort"))){
										statusMessageAddress = new InetSocketAddress(resourceMap.get("statusMessageIp"),Integer.parseInt(resourceMap.get("statusMessagePort")));	
									}else{
										statusMessageAddress = null;
									}
									boolean printOnECR = false;

									/**turns printOnEcr true if prinflag received is 1 or its R req **/
									if(resourceMap.get("printFlag").equals("1")||resourceMap.get("messageCode").equals("R")){
										printOnECR = true;
									}
									IPS = getContext().actorOf(IPS_Link.props(statusMessageAddress, terminalAddress, printOnECR),"IPS");
									IPS.tell(resourceMap, getSelf());
									getContext().setReceiveTimeout(Duration.create(95, TimeUnit.SECONDS));//setting receive timeout
									log.debug("SETTING RECEIVE TIMEOUT OF 95 SEC");
								}else{
									getContext().getSystem().stop(getSelf());
									log.error("ending connection......");
								}
							}else{
								log.error("Validation Failed..!");
								sendNack();
							}
						}else{
							sender.tell(TcpMessage.write(ByteString.fromString("WAIT !! -> Transaction In Progress..")), getSelf());
						}
					}else{
						log.error("UNKNOWN REQUEST..!");
						sendNack();
					}

				})
				.match(FinalReceipt.class, receipt->{
					log.info("sending out FINAL RECEIPT to "+clientIP.toString());
					sender.tell(TcpMessage.write(ByteString.fromString(receipt.getReceipt())), getSelf());
					context().system().stop(IPS);
					log.trace("=============================END---OF---LOG================================");
				}).match(StatusMessage.class, statusMessage->{
					log.info("sending out statusMessage to "+clientIP.toString());
					sender.tell(TcpMessage.write(ByteString.fromString(statusMessage.getStatusMessage())), getSelf());
				}).match(FailedAttempt.class, failedMessage->{
					log.info("sending out FAILURE to "+clientIP.toString());
					sender.tell(TcpMessage.write(ByteString.fromString(failedMessage.getMessage())), getSelf());
				})
				.match(String.class, s->{
					if(s.charAt(0)!=(char)06){
						log.info("sending out NACK to "+clientIP.toString()+" : "+s);  //if not ack then kill the ips actor
						if(IPS!=null){
							context().system().stop(IPS);
						}
					}else{
						log.info("sending out ACK to "+clientIP.toString()+" : "+s);
					}
					sender.tell(TcpMessage.write(ByteString.fromString(s)), getSelf());
				})
				.match(ReceiveTimeout.class, r -> {
					log.fatal("TIMEOUT EXCEEDED KILLING ALL");
					IPS.tell(PoisonPill.getInstance(), sender);//killing ips actor
					log.trace("turning off TIMER");
					getContext().setReceiveTimeout(Duration.Undefined());
				})
				.match(ConnectionClosed.class, closed->{
					log.debug("Server: Connection Closure"+closed);
					getContext().stop(getSelf());
				})
				.match(CommandFailed.class, conn->{
					log.fatal("Server: "+conn);
					getContext().stop(getSelf());
				})
				.build();
	}
	private boolean isValidated(HashMap<String, String> resourceMap){
		boolean result = false;
		/** !=null checks that if the particular fields were in json string received Not their values!!**/
		if(resourceMap.get("terminalIp")!= null && resourceMap.get("terminalPort")!= null && resourceMap.get("printFlag")!= null && resourceMap.get("messageCode")!= null && resourceMap.get("terminalIp")!= "" && resourceMap.get("terminalPort")!= "" && resourceMap.get("printFlag")!= "" && resourceMap.get("messageCode")!= ""){
			log.trace("VALIDATION PASSED------> 1");
			if((resourceMap.get("messageCode").equals("P")||resourceMap.get("messageCode").equals("A")) && (resourceMap.get("amount")!= null && resourceMap.get("GTbit")!= null && resourceMap.get("amount")!= "" && resourceMap.get("GTbit")!= "" /**&& resourceMap.get("GTmessage")!= null**/)){
				log.trace("VALIDATION PASSED------> 2");
				if((resourceMap.get("GTbit").equals("0")||resourceMap.get("GTbit").equals("1")) && (resourceMap.get("printFlag").equals("0")||resourceMap.get("printFlag").equals("1"))){
					log.trace("VALIDATION PASSED------> 3");
					result = true;
				}
			}else if(resourceMap.get("messageCode").equals("S") && resourceMap.get("GTbit")!= null && resourceMap.get("GTbit")!= "" /**&& resourceMap.get("GTmessage")!= null**/){
				if((resourceMap.get("GTbit").equals("0")||resourceMap.get("GTbit").equals("1")) && (resourceMap.get("printFlag").equals("0")||resourceMap.get("printFlag").equals("1"))){
					log.trace("VALIDATION PASSED------> 2");
					result = true;
				}
			}else if(resourceMap.get("messageCode").equals("D")||resourceMap.get("messageCode").equals("Z")||resourceMap.get("messageCode").equals("X")||resourceMap.get("messageCode").equals("R")||resourceMap.get("messageCode").equals("T")){
				if(resourceMap.get("printFlag").equals("0")||resourceMap.get("printFlag").equals("1")){
					log.trace("VALIDATION PASSED------> 2");
					result = true;
				}
			}
		}
		return result;
	}
	private void sendNack(){
		String NACK = String.valueOf((char)21);
		getSelf().tell(NACK+IPS_Link.calcIpsLRC(NACK), getSelf());
		log.info("UNKNOWN REQUEST-> sending NACK");		
	}
	private boolean validIpAndPort(String Ip, String Port){
		boolean result = false;
		if((Ip!=null||Port!=null) && (Ip!=""||Port!="")){
			if(Ip.matches("[0-9.]*") && Port.matches("[0-9]*")){
				result =  true;
			}
		}
		return result;
	}

	@Override
	public void postStop() throws Exception {
		getContext().parent().tell(clientIP, getSelf());
	//	db.removeConnection( clientIP.getHostString(), String.valueOf(clientIP.getPort()));
		//TcpServerActor.clientnum --;
		//System.err.println(TcpServerActor.clientnum);
	}

}