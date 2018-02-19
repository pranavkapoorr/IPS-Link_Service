package app_main;

import java.net.*;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Message_Resources.FailedAttempt;
import Message_Resources.FinalReceipt;
import Message_Resources.Protocol37Format;
import Message_Resources.StatusMessage;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.util.ByteString;
import core.StatusMessageSender;
import core.serial.SerialManager;
import core.tcp.TcpClientActor;
import protocol37.Protocol37UnformattedMessage;
import protocol37.ReceiptGenerator;

import java.io.*;


public class Link extends AbstractActor{
	//private final String propertiesPath ="resources/IPS-Link.properties";
	private final ActorRef communicationActor;
	private final ActorRef statusMessageListener;
	//private int printflag = 1;
	//private Properties config= new Properties();
	private final static Logger log = LogManager.getLogger(Link.class);
	//private String finalReceipt;
	private final ActorRef receiptGenerator;
	public static volatile long amount = 0;
	public static volatile boolean isAdvance;
	public static volatile boolean isTerminalStatus;
	public static volatile boolean isLastTransStatus;
	public static volatile boolean sendToTerminal;
	private volatile int connectionCycle;





	public static Props props(InetSocketAddress statusMessageIp , InetSocketAddress terminalAddress, boolean printOnECR) {
		return Props.create(Link.class , statusMessageIp, terminalAddress, printOnECR);
	}
	private Link(InetSocketAddress statusMessageIp , InetSocketAddress terminalAddress, boolean printOnECR) throws InterruptedException {
		//	try {
		//	InputStream in=new FileInputStream(propertiesPath);
		log.trace("=============================START---OF---LOG================================");
		//	log.info("loading properties file");
		//	config.load(in);
		//	in.close();
		//	} catch (FileNotFoundException e) {
		//		log.fatal("not found:"+e.getMessage());
		//	} catch (IOException e) {
		//      log.fatal(e.getMessage());
		//}
		//if(config.getProperty("Connection_Type").equalsIgnoreCase("TCP_IP")){
		log.info("setting Up Tcp Connection type");
		this.statusMessageListener = context().actorOf(StatusMessageSender.props(statusMessageIp), "status_message_senderActor");
		this.receiptGenerator = context().actorOf(ReceiptGenerator.props(printOnECR),"receipt_Generator_Actor");
		this.communicationActor =  getContext().actorOf(TcpClientActor.props(statusMessageListener,receiptGenerator,terminalAddress));
		/*}else if(config.getProperty("Connection_Type").equalsIgnoreCase("USB")){
			log.info("setting Up USB Connection type");
			this.communicationActor =  getContext().actorOf(SerialManager.props(config.getProperty("COM_Port")),"Serial");
		}else{
			log.error("CHECK CONNECTION TYPE IN PROPERTIES FILE!!! <look for additional spaces");
			System.exit(0);
		}*/
	}

	@Override
	public void preStart() throws Exception {
		isAdvance = false;
		isTerminalStatus = false;
		isLastTransStatus = false;
		sendToTerminal = false;
		connectionCycle = 0;
		log.trace("Starting IPS_LINK ACTOR");
	}

	/**** PAYMENT 
	 * @param printFlag -> 1 for printing on ECR and 0 for Printing on Terminal
	 * @param amountInPence -> amount in pence
	 * @param additionaldataGT -> 0 for not sending additional data to GT and 1 for sending additional data to GT and if 1 then additional U command should be sent too
	 *****/
	public void payment(int printFlag,long amountInPence,int additionaldataGT) {
			log.info("setting printing details to terminal with flag: "+printFlag);
			communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
			log.info("starting \"PAYMENT\" function");
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
		log.info("setting printing details to terminal with flag: "+printFlag);
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
		//TimeUnit.MILLISECONDS.sleep(320);
		log.info("starting \"EXTENDED PAYMENT\" function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.paymentExtended(amountInPence)), ActorRef.noSender());
	} 
	/** GET-TERMINAL-STATUS 
	 * **/
	public void getTerminalStatus(int printFlag){
		log.info("setting printing details to terminal with flag: "+printFlag);
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
		log.info("starting \"GET TERMINAL STATUS\" function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.terminalStatus()), ActorRef.noSender());
	}
	/**** REVERSAL **
	 * REVERSAL should be done after a Successful PAYMENT operation
	 ***/
	public void reversal(int printFlag, int additionaldataGT){
		log.info("setting printing details to terminal with flag: "+printFlag);
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
		log.info("starting \"REVERSAL\" function");
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
		log.info("setting printing details to terminal with flag: "+printFlag);
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
		log.info("starting \"REFUND\" function");
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
			log.info("setting printing details to terminal with flag: "+printFlag);
			communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
			log.info("Starting \"DLL FUNCTION\" with flag: "+dllFlag);
			communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.dllFunction(dllFlag)), ActorRef.noSender());
	}
	/****REPORT 
	 * @param reportFlag : <li> 0 for X Report.</li><li> 1 for Z report</li>
	 * @throws InterruptedException ****/
	public void Report(int printFlag, int reportFlag){
		log.info("setting printing details to terminal with flag: "+printFlag);
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.printOptions(printFlag)), ActorRef.noSender());
		log.info("starting \"REPORT\" Function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.report(reportFlag)), ActorRef.noSender());
	}
	/**** REPRINT-TICKET 
	 *****/
	public void reprintTicket(){
		log.info("Starting print receipt funtion");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.reprintTicket()), ActorRef.noSender());
	}
	/**** CHECKPAPER ****
	 * No details about it's usage.
	 */
	public void checkPaper(){
		log.info("starting checkPaper function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.checkPaper()), ActorRef.noSender());
	}
	public void posInformation(){
		log.info("starting posInformation function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.posInfo()), ActorRef.noSender());
	}
	public void additionalDataGT(String additionalData4GT) {
		isAdvance =  true;
		log.info("ADVANCED");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.additionalDataGT(additionalData4GT)), ActorRef.noSender());
	}
	/***Receipt-Recording
	 *@return:The response message is exactly the last stored EXIT message during the scheduled procedures.
	 */
	public void receiptRecording() {
		log.info("starting receipt recording");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.receiptRecording()), ActorRef.noSender());
	}
	public void useMagneticTapeCard( int readingTypeFlag) {
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.useMagneticTpeCard(readingTypeFlag)), ActorRef.noSender());
		//talkToTerminal(terminalIp, terminalPort, sendAcknowledgement());
	}
	public void restampPrint( int printFlag, int ticketType){
		log.info("starting resprint stamp function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.restampPrint(printFlag, ticketType)), ActorRef.noSender());
		//talkToTerminal(terminalIp, terminalPort, sendAcknowledgement());
	}
	public void startLocalTelephone(long speed_in_bps){
		log.info("starting startLocalTelephone function");
		communicationActor.tell(new Protocol37Format(Protocol37UnformattedMessage.startLocalTelephone(speed_in_bps)), ActorRef.noSender());
		//talkToTerminal(terminalIp, terminalPort, sendAcknowledgement());
	}
	private boolean isIPSRequest(String request){//not used
		boolean result = false;
		if(request.charAt(0)=='p' && request.charAt(request.length()-2)=='k'){
			log.debug("found starting and ending bits");
			String message = request.substring(0, request.length()-1);
			//System.err.println(message);
			//System.err.println(request);
			if(calcIpsLRC(message)==request.charAt(request.length()-1)){
				log.debug("found LRC and is valid");
				result = true;
			}
		}
		return result;
	}
	/**calcLRC 
	 * calculates LRC by Xoring all the bits of message 
	 * @param msg is the message for which lrc is calculated;
	 * @return Lrc character;
	 */
	public static char calcIpsLRC(String msg) {//not used
		int checksum=100;
		for (int i = 0; i < msg.length(); i++){
			checksum ^= msg.charAt(i);
		}
		return (char)checksum;
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(HashMap.class, resourceMapX->{
					if(sendToTerminal){
						log.info("Successfully connected to terminal at {} cycle",connectionCycle);
						@SuppressWarnings("unchecked")
						HashMap<String, String> resourceMap = resourceMapX;
						if(resourceMap.get("messageCode").equals("P")){
							String ACK = String.valueOf((char)06);
							//context().parent().tell(ACK+calcIpsLRC(ACK), getSelf());
							log.info("received PAYMENT REQUEST");
							long amount = Integer.parseInt((String) resourceMap.get("amount"));
							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
							int additonaldataGT = Integer.parseInt((String) resourceMap.get("GTbit"));
							if(additonaldataGT == 1){
								paymentAdvanced(printFlag, amount, resourceMap.get("GTmessage"));
							}else if(additonaldataGT == 0){
								payment(printFlag, amount,additonaldataGT);
							}
	
						}else if(resourceMap.get("messageCode").equals("A")){
							String ACK = String.valueOf((char)06);
							//context().parent().tell(ACK+calcIpsLRC(ACK), getSelf());
							log.info("received REFUND REQUEST");
							long amount = Integer.parseInt((String) resourceMap.get("amount"));
							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
							int additonaldataGT = Integer.parseInt((String) resourceMap.get("GTbit"));
							if(additonaldataGT == 1){
								refundAdvanced(printFlag, amount, resourceMap.get("GTmessage"));
							}else if(additonaldataGT == 0){
								refund(printFlag, amount,additonaldataGT);
							}
	
						}else if(resourceMap.get("messageCode").equals("S")){
							String ACK = String.valueOf((char)06);
							//context().parent().tell(ACK+calcIpsLRC(ACK), getSelf());
							log.info("received REVERSAL REQUEST");
							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
							int additonaldataGT = Integer.parseInt((String) resourceMap.get("GTbit"));
							if(additonaldataGT == 1){
								reversalAdvanced(printFlag,resourceMap.get("GTmessage"));
							}else if(additonaldataGT == 0){
								reversal(printFlag,additonaldataGT);
							}
	
						}else if(resourceMap.get("messageCode").equals("D")){
							String ACK = String.valueOf((char)06);
							//context().parent().tell(ACK+calcIpsLRC(ACK), getSelf());
							log.info("received FIRST DLL REQUEST");
							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
							dllFunctions(printFlag,1);
	
						}else if(resourceMap.get("messageCode").equals("M")){
							String ACK = String.valueOf((char)06);
							//context().parent().tell(ACK+calcIpsLRC(ACK), getSelf());
							log.info("received MANUAL DLL REQUEST");
							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
							dllFunctions(printFlag,0);
	
						}else if(resourceMap.get("messageCode").equals("X")){
							String ACK = String.valueOf((char)06);
							//context().parent().tell(ACK+calcIpsLRC(ACK), getSelf());
							log.info("received X-report REQUEST");
							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
							Report(printFlag,0);
	
						}else if(resourceMap.get("messageCode").equals("Z")){
							String ACK = String.valueOf((char)06);
							//context().parent().tell(ACK+calcIpsLRC(ACK), getSelf());
							log.info("received Z-report REQUEST");
							int printFlag = Integer.parseInt((String) resourceMap.get("printFlag"));
							Report(printFlag, 1);
						}else if(resourceMap.get("messageCode").equals("T")){
							String ACK = String.valueOf((char)06);
							//context().parent().tell(ACK+calcIpsLRC(ACK), getSelf());
							isTerminalStatus =  true;
							log.info("received TERMINAL-STATUS REQUEST");
							int printFlag = 1;//print on ECR always to avoid xreport receipt on ped
							getTerminalStatus(printFlag);
	
						}else if(resourceMap.get("messageCode").equals("R")){
							String ACK = String.valueOf((char)06);
							//context().parent().tell(ACK+calcIpsLRC(ACK), getSelf());
							log.info("received REPRINT TICKET REQUEST");
							reprintTicket();
	
						}else if(resourceMap.get("messageCode").equals("L")){
							String ACK = String.valueOf((char)06);
							isLastTransStatus = true;
							//context().parent().tell(ACK+calcIpsLRC(ACK), getSelf());
							log.info("received LAST TRANSACTION STATUS REQUEST");
							reprintTicket();
	
						}else {
							String NACK = String.valueOf((char)21);
							context().parent().tell(NACK+calcIpsLRC(NACK), getSelf());
							log.info("UNKNOWN REQUEST-> sending NACK");
						}
					}else{
						/***sending the received resourceMap to itself unless the connection with terminal is successful***/
						getSelf().tell(resourceMapX, getSelf());
						if(connectionCycle == 0){
							log.debug("haven't connected to terminal so resending to itself...");
						}
						connectionCycle ++;
						
					}
				})

				.match(FinalReceipt.class, r->{
					context().parent().tell(r, getSelf());
					//System.err.println(finalReceipt);
				})
				.match(FailedAttempt.class, f->{
					context().parent().tell(f, getSelf());
					context().stop(getSelf());
					//System.err.println(finalReceipt);
				})
				.match(StatusMessage.class, sM->{
					context().parent().tell(sM, getSelf());
					//System.err.println(finalReceipt);
				}).build();
	}
	

	@Override
	public void postStop() throws Exception {
		log.info("Stopping IPS_LINK ACTOR");
	}
	
	


}
