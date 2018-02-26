package protocol37;

import java.net.InetSocketAddress;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Message_Resources.GT37Message;
import Message_Resources.Protocol37Format;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.util.ByteString;
import core.serial.SSLTcpActor;

public class Protocol37ReadWriteHandler extends AbstractActor{
	private final static Logger log = LogManager.getLogger(Protocol37ReadWriteHandler.class);
	private ActorRef tcpGT = null;
	private final ActorRef receiptGenerator;
	private final ActorRef statusMessageSender;
	private String terminalIdX;
	private int CYCLE=1;
	//private long startTime;
	//final private long timeOutGT = 5000;
	//private long stopTime;
	//private int ENQ_received=0 ;
	private boolean enableSSL = true;
	public static Props props(ActorRef statusMessageListener, ActorRef receiptGenerator){
		return Props.create(Protocol37ReadWriteHandler.class , statusMessageListener, receiptGenerator);
	}
	public Protocol37ReadWriteHandler(ActorRef statusMessageListener, ActorRef receiptGenerator) {
		this.receiptGenerator = receiptGenerator;
		this.statusMessageSender = statusMessageListener;

	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				/**checks if the message received by this actor is String**/
				.match(String.class, msg->{
					/** checks if the received String is a Status Message starting with "SOH" and ending with "EOT" **/
					if(isStatusMessage(msg)){ //if status message
						String message = msg.substring(msg.indexOf((char)01)+1,msg.indexOf((char)04));
						log.info("Forwarding Status Message: " + message);
						statusMessageSender.tell("{\"statusMessage\":\""+message+"\"}", self());
					}
					/** checks if the received String is Acknowledgement starting with "ACK" and ending with LRC "z" **/
					else if(isACK(msg)){
						//	log.info("ACK :"+msg);
					}
					/** checks if the received String is Nack starting with "NACK" and ending with LRC "i" **/
					else if(isNACK(msg)){
						//log.debug("NACK :"+msg);
					}
					/** checks if the received String is a Application Message Starting with STX and ending with ETX **/
					else if(isProtocol37ApplicationMessage(msg)){ //if receipt message
						/** every STX ETX message received and sent requires ACK or Nack from the counter party so that Next Message can be sent 
						 * getsender() is the Serial Manager Actor who sends message to this Actor so Ack message is sent back to it**/
						getSender().tell(new Protocol37Format(Protocol37UnformattedMessage.ACK()),getSelf());
						String message = msg.substring(msg.indexOf((char)02)+1,msg.indexOf((char)03));
						log.info("Result: " + message);

						if(message.contains("0S")){
							receiptGenerator.tell(message, getSelf());
						}else if(message.contains("0E")||message.contains("0s0")||message.contains("0V")||message.contains("0A")||message.contains("0T")||message.contains("0C")||message.contains("0D")||message.contains("0U")){
							receiptGenerator.tell(message, getSelf());
						}




						/** checks if the received String is LINE OPENING REQUEST with STX and ending with ETx and has "L" message bit **/ 
						else if(isLineOpeningRequest(msg)){ //USB
							if(CYCLE>1){
								getContext().stop(tcpGT);
							}
							log.info("CYCLE : "+CYCLE);
							/** taking out Terminal Id from the received message **/
							terminalIdX = msg.substring(1,9);
							/** taking out GT Ip Address from received message **/
							String GT_Ip = msg.substring(18, 33);
							/** taking out GT Port from received message **/
							int GT_Port = Integer.parseInt(msg.substring(msg.indexOf('@')+1,msg.lastIndexOf('@')));
							/** Starting SSL-Tcp Actor to make secure tcp connection with the GT on the received GT Ip and Port **/
							log.info("openning connection with GT at : "+ GT_Ip +":"+GT_Port);
							tcpGT = getContext().actorOf(SSLTcpActor.props(new InetSocketAddress(GT_Ip, GT_Port), enableSSL),"tcp-ssl-actor"+CYCLE);

							/*	startTime = System.nanoTime();
									stopTime = startTime + (timeOutGT*1000*1000);
									log.info("Started Timer to wait for ENQ for {} milliseconds",timeOutGT);
									while(System.nanoTime() <= stopTime){
										if(System.nanoTime() == stopTime ){
											System.err.println("enq->>>>>"+ENQ_received);
											//	getContext().getParent().tell(new Protocol37Format(Protocol37UnformattedMessage.openLine(terminalId,"01")), getSelf());

										}
									}*/
							CYCLE++;

						}
						/** checks if the received String is LINE CLOSING REQUEST with STX and ending with ETx and has "l" message bit **/ 
						else if(isLineClosingRequest(msg)){ //USB
							/** closes the SSL-tcp-actor **/ 
							getContext().stop(tcpGT);
							/** sending CLOSELINE CONFIRMATION message to the terminal **/ 
							getContext().getParent().tell(new Protocol37Format(Protocol37UnformattedMessage.closeLine(terminalIdX,"00")), getSelf());//sending closing confirmation
						}
					}else{
						/**SENDING nack**/
						getSender().tell(new Protocol37Format(Protocol37UnformattedMessage.NACK()),getSelf());
					}
				})
				/** checks if the message received by this actor is byte[] which is the MESSAGE RECEIVED FROM TERMINAL OR GT to forward to each other depending upon Conditions **/
				.match(byte[].class, msg->{ //USB
					/** checks if the message is coming from Terminal starting with "@@@@" **/
					if(isTerminalToGTMessage(msg)){
						/** checks if SSL-TCP is already connected **/
						if(tcpGT!=null){
							//TimeUnit.MILLISECONDS.sleep(320);
							//System.out.println("BYTE[] from terminal 4 GT:"+msg);
							log.info("HEX FOR GT-> \n"+prettyOut(msg));
							/** decodes the data received from Terminal and send it to SSL-TCP ACTOR which sends to GT**/	
							tcpGT.tell(ByteString.fromArray(decodeForGT(msg)), getSelf());
						}else{
							log.fatal("GT NOT CONNECTED...!");
							throw new RuntimeException("GT NOT Connected!");
						}
					}
					/** checks that the message is coming from GT for terminal **/
					else{
						String messageFromGT = new String(msg);
						/** checks if the received message from GT is ENQ with ENQ bit **/
						if(isENQ_GT(messageFromGT)){
							log.trace("ENQ received from GT");
							//	ENQ_received ++;
							/** as ENQ received now sending LINE OPENING CONFIRMATION message to terminal **/
							getContext().getParent().tell(new Protocol37Format(Protocol37UnformattedMessage.openLine(terminalIdX,"00")), getSelf());
						}
						/** checks if the received message from GT is ACK with ACK bit **/
						else if(isACK_GT(messageFromGT)){
							log.trace("ACK received from GT");
						}
						/** checks if the received message from GT is NACK with Nack bit **/
						else if(isNACK_GT(messageFromGT)){
							log.fatal("NACK received from GT");
						}
						/** transaction message from GT **/
						else{ 
							log.trace("message received from GT");
						}
						//log.info(" encodeding: "+(messageFromGT));
						log.info("message for PED in Hex->:\n"+prettyOut(msg));
						/** encodes the message with "@@@@lenlenlenlenMessage" format and is sent to Terminal **/
						getContext().getParent().tell(new GT37Message(encodeForTerminal(msg)), getSelf());
					}
				}).build();
	}

	/**isACK
	 * @param msg : String message coming from Terminal 
	 * @return :true if following condition is met else false **/
	private boolean isACK(String msg){
		boolean result = false;
		if(msg.length()==3 && msg.charAt(0)==(char)06 && msg.charAt(2)==(char)122){
			result = true;
		}
		return result;
	}
	/**isNACK
	 * @param msg : String message coming from Terminal 
	 * @return :true if following condition is met else false **/
	private boolean isNACK(String msg){
		boolean result = false;
		if(msg.length()==3 && msg.charAt(0)==(char)21 && msg.charAt(2)==(char)105){
			result = true;
		}
		return result;
	}
	/**isStatusMessage
	 * @param msg : String message coming from Terminal 
	 * @return :true if following condition is met else false **/
	private boolean isStatusMessage(String msg){
		boolean result = false;
		if(msg.charAt(0)==(char)01 && msg.charAt(msg.length()-1)==(char)04){
			result = true;
		}
		return result;
	}
	/**isApplicationMessage
	 * @param msg : String message coming from Terminal 
	 * @return :true if following condition is met else false **/
	private boolean isProtocol37ApplicationMessage(String messageFromTerminal){
		log.info("Validating Received message calculating LRC");
		boolean result = false;
		if((messageFromTerminal.charAt(0)==(char)02 && messageFromTerminal.charAt(messageFromTerminal.length()-2)==(char)03)){
			log.info("STX ETX FOUND in message");
			if(messageFromTerminal.charAt(messageFromTerminal.length()-1) == Protocol37Format.calcLRC_P37(messageFromTerminal.substring(0, messageFromTerminal.length()-1))){
				result = true;
				log.info("Validated -> matched LRC");
			}else{
				log.error("Validation Failed ! -> unexpected LRC");
			}
		}
		return result;
	}
	/**isLineOpeningRequest
	 * @param msg : String message coming from Terminal 
	 * @return :true if following condition is met else false **/
	private boolean isLineOpeningRequest(String msg){ //USB
		boolean result = false;
		if(msg.contains("L000000")){
			result = true;
		}
		return result;
	}
	/**isLineClosingRequest
	 * @param msg : String message coming from Terminal 
	 * @return :true if following condition is met else false **/
	private boolean isLineClosingRequest(String msg){ //USB
		boolean result = false;
		if(msg.contains("0l0000000000")){
			result = true;
		}
		return result;
	}
	/**isTerminalToGT
	 * @param msg : byte[] message coming from Terminal/GT 
	 * @return :true if following condition is met else false **/
	private boolean isTerminalToGTMessage(byte[] msg){ //USB
		boolean result = false;
		if(msg[0]==64 && msg[1]==64){
			result = true;
		}
		return result;
	}
	/**isACK_GT
	 * @param msg : String message coming from GT 
	 * @return :true if following condition is met else false **/
	private boolean isACK_GT(String messageFromGT){ //USB
		boolean result = false;
		if(messageFromGT.length()==1 && messageFromGT.charAt(0)==(char)06){
			result = true;
		}
		return result;
	}
	/**isNACK_GT
	 * @param msg : String message coming from GT 
	 * @return :true if following condition is met else false **/
	private boolean isNACK_GT(String messageFromGT){ //USB
		boolean result = false;
		if(messageFromGT.length()==1 && messageFromGT.charAt(0)==(char)21){
			result = true;
		}
		return result;
	}
	/**isENQ_GT
	 * @param msg : String message coming from GT 
	 * @return :true if following condition is met else false **/
	private boolean isENQ_GT(String messageFromGT){ //USB
		boolean result = false;
		if(messageFromGT.length()==1 && messageFromGT.charAt(0)==(char)05){
			result = true;
		}
		return result;
	}

	/** decodeForGT 
	 * @param msg : the byte array received from TERMINAL
	 * @return : encoded byte array for GT
	 * **/
	private byte[] decodeForGT(byte[] msg) { //USB
		System.out.print("ascii -> ");
		for(int i=0; i<msg.length; i++){
			System.out.print(msg[i]+"");
		}
		System.out.println();
		System.out.print("message -> " );
		for(int i=0;i<msg.length;i++){
			System.out.print( (char)msg[i]);
		}
		System.out.println();
		byte[] temp = Arrays.copyOfRange(msg, 8, msg.length);
		return temp;
	}
	/** ecodeForTerminal 
	 * @param messageFromGT : the byte array received from GT
	 * @return : decoded byte array for terminal
	 ***/
	private byte[] encodeForTerminal(byte[] messageFromGT){ //USB
		byte[] temp = new byte[messageFromGT.length+8];
		String length = String.format("%04d", messageFromGT.length);
		byte[] header = new byte[8];
		int count=0;
		for(int i = 0; i< 8 ;i++){
			if(i<4){
				header[i] = (byte) 64;
			}
			if(i<8 && i>=4){
				header[i] = (byte) length.charAt(count);
				count++;
			}
		}
		/**Copying elements of the created byte array and received message array to main byte array**/
		System.arraycopy(header, 0, temp, 0, header.length);
		System.arraycopy(messageFromGT, 0, temp, header.length, messageFromGT.length);
		return temp;
		//return "@@@@"+String.format("%04d", messageFromGT.length())+messageFromGT;
	}
	public String prettyOut(byte[] msg) { //USB
		StringBuilder Result = new StringBuilder();
		for (int j = 1; j < msg.length+1; j++) {
			if (j % 16 == 1 || j == 0) {
				if( j != 0){
					Result.append("\n");
				}
				Result.append(String.format("%03d | ", j / 16));
			}
			Result.append(String.format("%02X ", msg[j-1]));
			if (j % 4 == 0) {
				Result.append("  ");
			}
		}
		return Result.toString();
	}

}
