package core.tcp;


import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import Message_Resources.FailedAttempt;
import Message_Resources.FinalReceipt;
import Message_Resources.IpsJson;
import Message_Resources.StatusMessage;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.Terminated;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.ConnectionClosed;
import akka.io.Tcp.Received;
import akka.io.TcpMessage;
import akka.util.ByteString;
import app_main.Link;
import scala.concurrent.duration.Duration;


public class TcpConnectionHandlerActor extends AbstractActor {

	private final static Logger log = LogManager.getLogger(TcpConnectionHandlerActor.class); 
	private final InetSocketAddress clientIP;
	private ActorRef sender;
	private ActorRef IPS;
	private volatile boolean ipsTerminated;
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
		log.trace("starting tcp-handler");
		ipsTerminated = false;
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
					if((messageX.startsWith("{") && messageX.endsWith("}")) || (messageX.startsWith("[") && messageX.endsWith("]"))){
						if(IPS==null ||IPS.isTerminated()){
							ObjectMapper mapper = new ObjectMapper();
							String message = messageX.replaceAll("'", "\\\"");
							IpsJson ips_message = null;
							try{
								ips_message = mapper.readValue(message, IpsJson.class);
								HashMap<String,String> resourceMap = ips_message.getParsedMap();
								if(isValidated(resourceMap)){
									log.trace("Incoming Json Validated..!");
									InetSocketAddress statusMessageAddress;
									InetSocketAddress terminalAddress;
									if(validIpAndPort(resourceMap.get("pedIp"),resourceMap.get("pedPort"))){		
										terminalAddress = new InetSocketAddress(resourceMap.get("pedIp"),Integer.parseInt(resourceMap.get("pedPort")));
										if(validIpAndPort(resourceMap.get("statusMessageIp"),resourceMap.get("statusMessagePort"))){
											statusMessageAddress = new InetSocketAddress(resourceMap.get("statusMessageIp"),Integer.parseInt(resourceMap.get("statusMessagePort")));	
										}else{
											statusMessageAddress = null;
										}
										boolean printOnECR = false;
	
										/**turns printOnEcr true if prinflag received is 1 or its R req **/
										if(resourceMap.get("printFlag").equals("1")||resourceMap.get("operationType").equals("ReprintReceipt")||resourceMap.get("operationType").equals("LastTransactionStatus")){
											printOnECR = true;
										}/*else if(resourceMap.get("messageCode").equals("ProbePed")){
										    printOnECR = false;
										}*/
										int timeout = 95;//default timeout
										if(resourceMap.get("timeOut")!=null && resourceMap.get("timeOut").matches("[0-9]*")
												){
											timeout = Integer.parseInt(resourceMap.get("timeOut"));
										}
										IPS = getContext().actorOf(Link.props(statusMessageAddress, terminalAddress, printOnECR),"IPS");
										context().watch(IPS);
										ipsTerminated = false;
										IPS.tell(resourceMap, getSelf());
										getContext().setReceiveTimeout(Duration.create(timeout, TimeUnit.SECONDS));//setting receive timeout
										log.debug("SETTING RECEIVE TIMEOUT OF {} SEC",timeout);
									}else{
										sendNack("wrong ped Ip or port..!",true);
									}
								}else{
									log.error("Validation Failed..!");
									sendNack("UNKNOWN REQUEST",true);
								}
							}catch (JsonParseException e) {
								log.error("Parsing error: -> "+e.getMessage());
								sendNack("UNKNOWN REQUEST+",true);
							}
						}else{
							log.debug("WAIT !! -> Transaction In Progress..");
							sendNack("WAIT !! -> Transaction In Progress..", false);
							//sender.tell(TcpMessage.write(ByteString.fromString()), getSelf());
						}
					}else{
						log.error("UNKNOWN REQUEST..!");
						sendNack("UNKNOWN REQUEST..!", true);
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
					if(s.contains("Error")){
						log.info("sending out NACK to "+clientIP.toString()+" : "+s);  //if not ack then kill the ips actor
					//	if(IPS!=null){
						//	context().system().stop(IPS);
					}
					sender.tell(TcpMessage.write(ByteString.fromString(s)), getSelf());
				})
				.match(ReceiveTimeout.class, r -> {
					if(!ipsTerminated){
						log.debug("TIMEOUT.....!!");
						sendNack("Timeout..!!", false);
						IPS.tell(PoisonPill.getInstance(), sender);//killing ips actor
					}
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
				.match(Terminated.class,s->{
					ipsTerminated = s.existenceConfirmed();
				})
				.build();
	}
	private boolean isValidated(HashMap<String, String> resourceMap){
		boolean result = false;
		/** !=null checks that if the particular fields were in json string received Not their values!!**/
		if(resourceMap.get("pedIp")!= null && resourceMap.get("pedPort")!= null && resourceMap.get("printFlag")!= null && resourceMap.get("operationType")!= null && resourceMap.get("pedIp")!= "" && resourceMap.get("pedPort")!= "" && resourceMap.get("printFlag")!= "" && resourceMap.get("operationType")!= ""){
			log.trace("VALIDATION PASSED------> 1");
			if((resourceMap.get("operationType").equals("Payment")||resourceMap.get("operationType").equals("Refund")) && (resourceMap.get("amount")!= null && resourceMap.get("amount")!= "" /* && resourceMap.get("GTbit")!= null  && resourceMap.get("GTbit")!= "" */&& resourceMap.get("transactionReference")!= null)){
				log.trace("VALIDATION PASSED------> 2");
				if(/*(resourceMap.get("GTbit").equals("0")||resourceMap.get("GTbit").equals("1")) &&*/ (resourceMap.get("printFlag").equals("0")||resourceMap.get("printFlag").equals("1"))){
					log.trace("VALIDATION PASSED------> 3");
					result = true;
				}
			}else if(resourceMap.get("operationType").equals("Reversal") /*&& resourceMap.get("GTbit")!= null && resourceMap.get("GTbit")!= ""*/ && resourceMap.get("transactionReference")!= null){
				if(/*(resourceMap.get("GTbit").equals("0")||resourceMap.get("GTbit").equals("1")) &&*/ (resourceMap.get("printFlag").equals("0")||resourceMap.get("printFlag").equals("1"))){
					log.trace("VALIDATION PASSED------> 2");
					result = true;
				}
			}else if(resourceMap.get("operationType").equals("FirstDll")||resourceMap.get("operationType").equals("UpdateDll")||resourceMap.get("operationType").equals("EndOfDay")||resourceMap.get("operationType").equals("PedBalance")){
				if(resourceMap.get("printFlag").equals("0")||resourceMap.get("printFlag").equals("1")){
					log.trace("VALIDATION PASSED------> 2");
					result = true;
				}
			}
			else if(resourceMap.get("operationType").equals("ProbePed")){
                if(resourceMap.get("printFlag").equals("0")){
                    log.trace("VALIDATION PASSED------> 2");
                    result = true;
                }
            }
			else if(resourceMap.get("operationType").equals("LastTransactionStatus")||resourceMap.get("operationType").equals("ReprintReceipt")||resourceMap.get("operationType").equals("PedStatus")){
                if(resourceMap.get("printFlag").equals("1")){
                    log.trace("VALIDATION PASSED------> 2");
                    result = true;
                }
            }
		}
		return result;
	}
	private void sendNack(String errorText , boolean endConnection){
		String errorToSend = "{\"errorText\":\"Error -> "+errorText+"\"}";
			getSelf().tell(errorToSend, getSelf());
			log.info("-> sending Error Message");	
			if(endConnection){
				getSelf().tell(PoisonPill.getInstance(), getSelf());
				log.error("ending connection......");
				}
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
		log.trace("stopping tcp-handler");
		getContext().parent().tell(clientIP, getSelf());
	//	db.removeConnection( clientIP.getHostString(), String.valueOf(clientIP.getPort()));
		//TcpServerActor.clientnum --;
		//System.err.println(TcpServerActor.clientnum);
	}

}