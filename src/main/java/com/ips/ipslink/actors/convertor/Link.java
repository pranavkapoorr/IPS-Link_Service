package com.ips.ipslink.actors.convertor;

import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ips.ipslink.actormessages.FailedAttempt;
import com.ips.ipslink.actormessages.FinalReceipt;
import com.ips.ipslink.actormessages.Protocol37Format;
import com.ips.ipslink.actormessages.StatusMessage;
import com.ips.ipslink.actors.protocol37.ReceiptGenerator;
import com.ips.ipslink.actors.protocol37.StatusMessageSender;
import com.ips.ipslink.actors.tcp.TcpClientActor;
import com.ips.ipslink.protocol37.Protocol37UnformattedMessage;
import com.ips.ipslink.resources.LanguageLoader;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;


public class Link extends AbstractActor {
	private final ActorRef communicationActor;
	private final ActorRef statusMessageListener;
	private final static Logger log = LogManager.getLogger(Link.class);
	private final ActorRef receiptGenerator;
	public static volatile long amount = 0;
	public static volatile boolean isAdvance;
	public static volatile boolean isTerminalStatus;
	public static volatile boolean isLastTransStatus;
	public static volatile boolean sendToTerminal;
	public static volatile boolean wait4CardRemoval;
	public static volatile boolean cardRemoved;
	private volatile int connectionCycle;
	
	
	public static Props props(InetSocketAddress statusMessageIp , InetSocketAddress terminalAddress, boolean printOnECR ,String clientIp,HashMap<String, ArrayList<String>> languageDictionary) {
		return Props.create(Link.class , statusMessageIp, terminalAddress, printOnECR , clientIp,languageDictionary);
	}
	private Link(InetSocketAddress statusMessageIp , InetSocketAddress terminalAddress, boolean printOnECR , String clientIp,HashMap<String, ArrayList<String>> languageDictionary) throws InterruptedException {
		log.info(getSelf().path().name()+" setting Up Tcp Connection type");
		this.statusMessageListener = context().actorOf(StatusMessageSender.props(statusMessageIp,clientIp, languageDictionary), "status_message_senderActor-"+clientIp);
		this.receiptGenerator = context().actorOf(ReceiptGenerator.props(printOnECR, languageDictionary),"receipt_Generator_Actor-"+clientIp);
		this.communicationActor =  getContext().actorOf(TcpClientActor.props(statusMessageListener,receiptGenerator,terminalAddress, clientIp),"TcpClient-"+clientIp);
	}

	@Override
	public void preStart() throws Exception {
	    cardRemoved = false;
	    wait4CardRemoval = false;
		isAdvance = false;
		isTerminalStatus = false;
		isLastTransStatus = false;
		sendToTerminal = false;
		connectionCycle = 0;
		log.trace(getSelf().path().name()+" Starting IPS_LINK ACTOR");
	}

	/**** PAYMENT 
	 * @param printFlag -> 1 for printing on ECR and 0 for Printing on Terminal
	 * @param amountInPence -> amount in pence
	 * @param additionaldataGT -> 0 for not sending additional data to GT and 1 for sending additional data to GT and if 1 then additional U command should be sent too
	 *****/
	public void payment(int printFlag,long amountInPence,int additionaldataGT) {
			log.debug(getSelf().path().name()+" setting printing details to terminal with flag: "+printFlag);
			communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
			log.info(getSelf().path().name()+" starting \"PAYMENT\" function");
			amount = amountInPence;
			communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.payment(additionaldataGT,amountInPence)), ActorRef.noSender());
	} 
	/**** Advanced-PAYMENT 
	 *****/
	public void paymentAdvanced(int printFlag,long amountInPence,String data4GT) {
		payment(printFlag, amountInPence,1);
		additionalDataGT(data4GT);
	} 
	/**** extended-PAYMENT 
	 *****/
	public void paymentExtended(int printFlag, long amountInPence) {
		log.info(getSelf().path().name()+" setting printing details to terminal with flag: "+printFlag);
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
		log.info(getSelf().path().name()+" starting \"EXTENDED PAYMENT\" function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.paymentExtended(amountInPence)), ActorRef.noSender());
	} 
	/** GET-TERMINAL-STATUS 
	 * **/
	public void getTerminalStatus(int printFlag){
		log.info(getSelf().path().name()+" setting printing details to terminal with flag: "+printFlag);
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
		log.info(getSelf().path().name()+" starting \"GET TERMINAL STATUS\" function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.terminalStatus()), ActorRef.noSender());
	}
	/**** REVERSAL **
	 * REVERSAL should be done after a Successful PAYMENT operation
	 ***/
	public void reversal(int printFlag, int additionaldataGT){
		log.info(getSelf().path().name()+" setting printing details to terminal with flag: "+printFlag);
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
		log.info(getSelf().path().name()+" starting \"REVERSAL\" function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.reversal(additionaldataGT)), ActorRef.noSender());
	}
	/**** Advanced-Reversal 
	 *****/
	public void reversalAdvanced(int printFlag,String data4GT) {
		reversal(printFlag,1);
		additionalDataGT(data4GT);
	} 
	/** REFUND 
	 * */
	public void refund(int printFlag,long amountInPence,int additionaldataGT) {
		log.info(getSelf().path().name()+" setting printing details to terminal with flag: "+printFlag);
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
		log.info(getSelf().path().name()+" starting \"REFUND\" function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.refund(additionaldataGT ,amountInPence)), ActorRef.noSender());			
	}
	/**** Advanced-Refund 
	 *****/
	public void refundAdvanced(int printFlag,long amountInPence,String data4GT) {
		refund(printFlag, amountInPence,1);
		additionalDataGT(data4GT);
	} 
	/** <strong>FIRST-DLL</strong>
	 * @param terminalIp :ip address of terminal.
	 * @param terminalPort :port of terminal.
	 * @param dllFlag : <li> 0 for manual dll.</li><li> 1 for first dll or prime dll environment.</li><li> 2 return aquirer data.</li>
	 * @throws InterruptedException 
	 * */
	public void dllFunctions(int printFlag, int dllFlag) {
			log.info(getSelf().path().name()+" setting printing details to terminal with flag: "+printFlag);
			communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
			log.info(getSelf().path().name()+" Starting \"DLL FUNCTION\" with flag: "+dllFlag);
			communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.dllFunction(dllFlag)), ActorRef.noSender());
	}
	/****REPORT 
	 * @param reportFlag : <li> 0 for X Report.</li><li> 1 for Z report</li>
	 * @throws InterruptedException ****/
	public void Report(int printFlag, int reportFlag){
		log.info(getSelf().path().name()+" setting printing details to terminal with flag: "+printFlag);
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
		log.info(getSelf().path().name()+" starting \"REPORT\" Function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.report(reportFlag)), ActorRef.noSender());
	}
	/**** REPRINT-TICKET 
	 *****/
	public void reprintTicket(){
		log.info(getSelf().path().name()+" Starting print receipt funtion");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.reprintTicket()), ActorRef.noSender());
	}
	/**** CHECKPAPER ****
	 * No details about it's usage.
	 */
	public void checkPaper(){
		log.info(getSelf().path().name()+" starting checkPaper function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.checkPaper()), ActorRef.noSender());
	}
	public void probePed(){
		log.info(getSelf().path().name()+" starting PROBE-PED(posInformation) function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.posInfo()), ActorRef.noSender());
	}
	public void additionalDataGT(String additionalData4GT) {
		isAdvance =  true;
		log.info(getSelf().path().name()+" ADVANCED");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.additionalDataGT(additionalData4GT)), ActorRef.noSender());
	}
	/***Receipt-Recording
	 *@return:The response message is exactly the last stored EXIT message during the scheduled procedures.
	 */
	public void receiptRecording() {
		log.info(getSelf().path().name()+" starting receipt recording");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.receiptRecording()), ActorRef.noSender());
	}
	public void useMagneticTapeCard( int readingTypeFlag) {
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.useMagneticTpeCard(readingTypeFlag)), ActorRef.noSender());
	}
	public void restampPrint( int printFlag, int ticketType){
		log.info(getSelf().path().name()+" starting resprint stamp function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.restampPrint(printFlag, ticketType)), ActorRef.noSender());
	}
	public void startLocalTelephone(long speed_in_bps){
		log.info(getSelf().path().name()+" starting startLocalTelephone function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.startLocalTelephone(speed_in_bps)), ActorRef.noSender());
	}
	
	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(HashMap.class, resourceMapX->{
					if(sendToTerminal){
						log.trace(getSelf().path().name()+" Successfully connected to terminal at {} cycle",connectionCycle);
						@SuppressWarnings("unchecked")
						HashMap<String, String> resourceMap = resourceMapX;
						if(resourceMap.get("operationType").equals("Payment")){
							log.info(getSelf().path().name()+" received PAYMENT REQUEST");
							/**checks if amount is between 1 pence to 100000**/
							if(resourceMap.get("amount").length()>0 && resourceMap.get("amount").length()<9){
    							long amount = Integer.parseInt((String) resourceMap.get("amount"));
    							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
    							if(resourceMap.get("wait4CardRemoved")!=null && resourceMap.get("wait4CardRemoved").equalsIgnoreCase("true")){
    							    wait4CardRemoval = true;
    							    log.info(getSelf().path().name()+" wait 4 card removed set to true...");
    							}
    								paymentAdvanced(printFlag, amount, resourceMap.get("transactionReference"));
							}else{
							    getContext().getParent().tell(new FailedAttempt("{\"errorText\":\"Error -> Amount should be between 10 to 10000000\"}"), getSelf());
							    getSelf().tell(PoisonPill.getInstance(), getSelf());
							}
	
						}else if(resourceMap.get("operationType").equals("Refund")){
							log.info(getSelf().path().name()+" received REFUND REQUEST");
							/**checks if amount is between 1 pence to 100000**/
                            if(resourceMap.get("amount").length()>0 && resourceMap.get("amount").length()<9){
    							long amount = Integer.parseInt((String) resourceMap.get("amount"));
    							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
    							if(resourceMap.get("wait4CardRemoved")!=null && resourceMap.get("wait4CardRemoved").equalsIgnoreCase("true")){
                                    wait4CardRemoval = true;
                                    log.info(getSelf().path().name()+" wait 4 card removed set to true...");
                                }
    								refundAdvanced(printFlag, amount, resourceMap.get("transactionReference"));
    							
                            }else{
                                getContext().getParent().tell(new FailedAttempt("{\"errorText\":\"Error -> Amount should be between 10 to 10000000\"}"), getSelf());
                                getSelf().tell(PoisonPill.getInstance(), getSelf());
                            }
	
						}else if(resourceMap.get("operationType").equals("Reversal")){
							log.info(getSelf().path().name()+" received REVERSAL REQUEST");
							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
							if(resourceMap.get("wait4CardRemoved")!=null && resourceMap.get("wait4CardRemoved").equalsIgnoreCase("true")){
                                wait4CardRemoval = true;
                                log.info(getSelf().path().name()+" wait 4 card removed set to true...");
                            }
								reversalAdvanced(printFlag,resourceMap.get("transactionReference"));
						
						}else if(resourceMap.get("operationType").equals("FirstDll")){
							log.info(getSelf().path().name()+" received FIRST DLL REQUEST");
							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
							dllFunctions(printFlag,1);
	
						}else if(resourceMap.get("operationType").equals("UpdateDll")){
							log.info(getSelf().path().name()+" received UPDATE DLL REQUEST");
							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
							dllFunctions(printFlag,0);
	
						}else if(resourceMap.get("operationType").equals("PedBalance")){
							log.info(getSelf().path().name()+" received PedBalance or X-report REQUEST");
							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
							Report(printFlag,0);
	
						}else if(resourceMap.get("operationType").equals("EndOfDay")){
							log.info(getSelf().path().name()+" received EndOfDay or Z-report REQUEST");
							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
							Report(printFlag, 1);
						}else if(resourceMap.get("operationType").equals("PedStatus")){
							isTerminalStatus =  true;
							log.info(getSelf().path().name()+" received Ped-STATUS REQUEST");
							int printFlag = 1;//print on ECR always to avoid xreport receipt on ped
							getTerminalStatus(printFlag);
	
						}else if(resourceMap.get("operationType").equals("ReprintReceipt")){
							log.info(getSelf().path().name()+" received REPRINT TICKET REQUEST");
							reprintTicket();
	
						}else if(resourceMap.get("operationType").equals("LastTransactionStatus")){
							isLastTransStatus = true;
							log.info(getSelf().path().name()+" received LAST TRANSACTION STATUS REQUEST");
							reprintTicket();
							
						}else if(resourceMap.get("operationType").equals("ProbePed")){
                            log.info(getSelf().path().name()+" received  ProbePed REQUEST");
                            probePed();
                            
                        }
						
					}else{
					    TimeUnit.NANOSECONDS.sleep(1);
						/***sending the received resourceMap to itself unless the connection with terminal is successful***/
						getSelf().tell(resourceMapX, getSelf());
						if(connectionCycle == 0){
							log.debug(getSelf().path().name()+" haven't connected to terminal so resending to itself...");
						}
						connectionCycle ++;
						
					}
				})

				.match(FinalReceipt.class, r->{
					context().parent().tell(r, getSelf());
					
				})
				.match(FailedAttempt.class, f->{
					context().parent().tell(f, getSelf());
					context().stop(getSelf());
					
				})
				.match(StatusMessage.class, sM->{
					context().parent().tell(sM, getSelf());
					
				}).build();
	}
	

	@Override
	public void postStop() throws Exception {
		log.info(getSelf().path().name()+" Stopping IPS_LINK ACTOR");
	}
	
	


}
