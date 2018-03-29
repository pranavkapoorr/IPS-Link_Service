package com.ips.ipslink.actors.tcp;


import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ips.ipslink.actormessages.FailedAttempt;
import com.ips.ipslink.actormessages.FinalReceipt;
import com.ips.ipslink.actormessages.StatusMessage;
import com.ips.ipslink.actors.convertor.Link;
import com.ips.ipslink.marshallers.IpsJson;

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
import scala.concurrent.duration.Duration;


public class TcpConnectionHandlerActor extends AbstractActor {

	private final static Logger log = LogManager.getLogger(TcpConnectionHandlerActor.class); 
	private String clientIP;
	private ActorRef sender;
	private ActorRef IPS;
	private volatile boolean ipsTerminated;
	final HashMap<String, ArrayList<String>> languageDictionary;
	public TcpConnectionHandlerActor(String clientIP,HashMap<String, ArrayList<String>> languageDictionary) {
	    this.languageDictionary = languageDictionary;
		this.clientIP = clientIP;
	}

	public static Props props(String clientIP, HashMap<String, ArrayList<String>> languageDictionary) {
		return Props.create(TcpConnectionHandlerActor.class, clientIP, languageDictionary);
	}
	
	@Override
	public void preStart() throws Exception {
	    log.trace("=============================START---OF---LOG================================");
		log.trace(getSelf().path().name()+" starting tcp-handler");
		ipsTerminated = false;
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Received.class, msg->{
					sender = getSender();
					String messageX = msg.data().utf8String();
					log.info(getSelf().path().name()+" received-tcp: "+ messageX);
					if((messageX.startsWith("{") && messageX.endsWith("}")) || (messageX.startsWith("[") && messageX.endsWith("]"))){
						if(IPS==null ||IPS.isTerminated()){
							ObjectMapper mapper = new ObjectMapper();
							String message = messageX.replaceAll("'", "\\\"");
							IpsJson ips_message = null;
							try{
								ips_message = mapper.readValue(message, IpsJson.class);
								HashMap<String,String> resourceMap = ips_message.getParsedMap();
								if(isValidated(resourceMap)){
									log.trace(getSelf().path().name()+" Incoming Json Validated..!");
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
										IPS = getContext().actorOf(Link.props(statusMessageAddress, terminalAddress, printOnECR,clientIP , languageDictionary),"IPS-"+clientIP);
										context().watch(IPS);
										ipsTerminated = false;
										IPS.tell(resourceMap, getSelf());
										getContext().setReceiveTimeout(Duration.create(timeout, TimeUnit.SECONDS));//setting receive timeout
										log.debug(getSelf().path().name()+" SETTING RECEIVE TIMEOUT OF {} SEC",timeout);
									}else{
										sendNack("06","WRONG PED IP OR PORT..!",true);
									}
								}else{
									log.error(getSelf().path().name()+" Validation Failed..!");
									sendNack("02","UNKNOWN REQUEST..!",true);
								}
							}catch (JsonParseException e) {
								log.error(getSelf().path().name()+" Parsing error: -> "+e.getMessage());
								sendNack("01","WRONG REQUEST..!",true);
							}
						}else{
							log.debug(getSelf().path().name()+" WAIT !! -> Transaction In Progress..");
							sendNack("08","WAIT !! -> Transaction In Progress..", false);
							//sender.tell(TcpMessage.write(ByteString.fromString()), getSelf());
						}
					}else{
						log.error(getSelf().path().name()+" UNKNOWN REQUEST..!");
						sendNack("00","UNKNOWN REQUEST..JSON EXPECTED!", true);
					}

				})
				.match(FinalReceipt.class, receipt->{
					log.info(getSelf().path().name()+" sending out FINAL RECEIPT to "+clientIP.toString());
					sender.tell(TcpMessage.write(ByteString.fromString(receipt.getReceipt())), getSelf());
					context().system().stop(IPS);
				}).match(StatusMessage.class, statusMessage->{
					log.info(getSelf().path().name()+" sending out statusMessage to "+clientIP.toString());
					sender.tell(TcpMessage.write(ByteString.fromString(statusMessage.getStatusMessage())), getSelf());
				}).match(FailedAttempt.class, failedMessage->{
					log.info(getSelf().path().name()+" sending out FAILURE to "+clientIP.toString());
					sender.tell(TcpMessage.write(ByteString.fromString(failedMessage.getMessage())), getSelf());
				})
				.match(String.class, s->{
					if(s.contains("Error")){
						log.info(getSelf().path().name()+" sending out NACK to "+clientIP.toString()+" : "+s);  //if not ack then kill the ips actor
					//	if(IPS!=null){
						//	context().system().stop(IPS);
					}
					sender.tell(TcpMessage.write(ByteString.fromString(s)), getSelf());
				})
				.match(ReceiveTimeout.class, r -> {
					if(!ipsTerminated){
						log.debug(getSelf().path().name()+" TIMEOUT.....!!");
						sendNack("09","Timeout..!!", false);
						IPS.tell(PoisonPill.getInstance(), sender);//killing ips actor
					}
					log.trace(getSelf().path().name()+" turning off TIMER");
					getContext().setReceiveTimeout(Duration.Undefined());
				})
				.match(ConnectionClosed.class, closed->{
					log.debug(getSelf().path().name()+" Server: Connection Closure "+closed);
					getContext().stop(getSelf());
				})
				.match(CommandFailed.class, conn->{
					log.fatal(getSelf().path().name()+" Server: Connection Failed "+conn);
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
			log.trace(getSelf().path().name()+" VALIDATION PASSED------> 1");
			if((resourceMap.get("operationType").equals("Payment")||resourceMap.get("operationType").equals("Refund")) && (resourceMap.get("amount")!= null && resourceMap.get("amount")!= "" /* && resourceMap.get("GTbit")!= null  && resourceMap.get("GTbit")!= "" */&& resourceMap.get("transactionReference")!= null)){
				log.trace(getSelf().path().name()+" VALIDATION PASSED------> 2");
				if(/*(resourceMap.get("GTbit").equals("0")||resourceMap.get("GTbit").equals("1")) &&*/ (resourceMap.get("printFlag").equals("0")||resourceMap.get("printFlag").equals("1"))){
					log.trace(getSelf().path().name()+" VALIDATION PASSED------> 3");
					result = true;
				}
			}else if(resourceMap.get("operationType").equals("Reversal") /*&& resourceMap.get("GTbit")!= null && resourceMap.get("GTbit")!= ""*/ && resourceMap.get("transactionReference")!= null){
				if(/*(resourceMap.get("GTbit").equals("0")||resourceMap.get("GTbit").equals("1")) &&*/ (resourceMap.get("printFlag").equals("0")||resourceMap.get("printFlag").equals("1"))){
					log.trace(getSelf().path().name()+" VALIDATION PASSED------> 2");
					result = true;
				}
			}else if(resourceMap.get("operationType").equals("FirstDll")||resourceMap.get("operationType").equals("UpdateDll")||resourceMap.get("operationType").equals("EndOfDay")||resourceMap.get("operationType").equals("PedBalance")){
				if(resourceMap.get("printFlag").equals("0")||resourceMap.get("printFlag").equals("1")){
					log.trace(getSelf().path().name()+" VALIDATION PASSED------> 2");
					result = true;
				}
			}
			else if(resourceMap.get("operationType").equals("ProbePed")){
                if(resourceMap.get("printFlag").equals("0")){
                    log.trace(getSelf().path().name()+" VALIDATION PASSED------> 2");
                    result = true;
                }
            }
			else if(resourceMap.get("operationType").equals("LastTransactionStatus")||resourceMap.get("operationType").equals("ReprintReceipt")||resourceMap.get("operationType").equals("PedStatus")){
                if(resourceMap.get("printFlag").equals("1")){
                    log.trace(getSelf().path().name()+" VALIDATION PASSED------> 2");
                    result = true;
                }
            }
		}
		return result;
	}
	private void sendNack(String errorCode, String errorText , boolean endConnection){
		String errorToSend = "{\"errorCode\":\""+errorCode+"\",\"errorText\":\"Error -> "+errorText+"\"}";
			getSelf().tell(errorToSend, getSelf());
			log.info(getSelf().path().name()+" -> sending Error Message");	
			if(endConnection){
				getSelf().tell(PoisonPill.getInstance(), getSelf());
				log.error(getSelf().path().name()+" ending connection......");
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
		log.trace(getSelf().path().name()+" stopping tcp-handler");
		log.trace("=============================END---OF---LOG================================");
	}

}