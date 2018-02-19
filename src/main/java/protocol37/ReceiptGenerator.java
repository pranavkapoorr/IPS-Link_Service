package protocol37;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import Message_Resources.FinalReceipt;
import Message_Resources.ReceiptJson;
import akka.actor.AbstractActor;
import akka.actor.Props;
import app_main.Link;

public class ReceiptGenerator extends AbstractActor{
	private final static Logger log = LogManager.getLogger(ReceiptGenerator.class);
	private StringBuffer receiptBuffer = new StringBuffer();
	StringBuffer output = new StringBuffer();
	StringBuilder receipt = new StringBuilder();
	private final ObjectMapper mapper;
	//private Database database = new Database();
	private final boolean  printOnECR;
	public static Props props(boolean printOnECR){
		return Props.create(ReceiptGenerator.class , printOnECR);
	}
	public ReceiptGenerator(boolean printOnECR) {
		this.printOnECR = printOnECR;
		this.mapper = new ObjectMapper();
	}
	
	@Override
	public void preStart() throws Exception {
	log.info("starting Receipt Generator");
	}
	@Override
	public Receive createReceive() {
		return receiveBuilder().match(String.class, message->{
			
		/***checks if the current String received is a receipt message and then puts it in to String buffer for formatting it***/
				if(message.contains("0S")){
					/***removes terminal id from each message**/
					receiptBuffer.append(message.substring(10, message.length()));
					/**checks if this is the last cycle of receipt depending on the delimiter**/
					if(message.charAt(message.length()-1) == (char)27){//last cycle
						String tempReceipt =  receiptBuffer.toString().replace(String.valueOf((char)127), "").replaceAll("}", "").replace(String.valueOf((char)27), "");
						receiptBuffer = null;
						receipt.append(tempReceipt);
						/**formatting receipt adding "/n" **/
						for(int i = 24; i< receipt.length(); i+=25){
							receipt.insert(i,"\n");
						}
						output.append("\n"+receipt+"\n;");
					//	parseReceipt(receipt.toString());
						/**sends out the receipt if printOnECR is enabled ie no S message will be expected but U message will be if GT bit is on**/
						if(printOnECR ){
							log.info("generating receipt...!");
							log.trace(output.toString());
							ReceiptJson receipt_Json = new ReceiptJson();
							if(Link.isLastTransStatus){
								String status = lastTransStatus(output.toString());
								receipt_Json.setReceipt(status);
							}else{
								receipt_Json.setReceipt(output.toString());
							}
							String out = mapper.writeValueAsString(receipt_Json);
							log.trace("JSON -> "+out);
							getContext().getParent().tell(new FinalReceipt(out), getSelf()); ///writing out the receipt
							out = null;
							receipt_Json = null;
							output = null;
							//if(IPS_Link.isAdvance){
							//	IPS_Link.isAdvance = false;
						//	}
						}
					}
					
					
				}
				/**checks if the received message is result of reversal, payment etc**/
				else if(message.contains("0E0")){
					String aquirerCode = "03";
					String terminalId = message.substring(0, 8);
					String cardType = message.substring(47, 48);
					String aquirerId = message.substring(48,59);
					String STAN = message.substring(59,65);
					String progressiveNum = message.substring(65,71);
					String actionCode = message.substring(71,74);
					if(message.substring(message.indexOf('E')+1, message.indexOf('E')+3).equalsIgnoreCase("00")){
						
						String cardPan = message.substring(12,31);
						String transacType = message.substring(31,34);
						String authCode = message.substring(34,40);
						String transTime = message.substring(40,47);
						
						if(Link.isAdvance){
							output.append(terminalId+";"+String.format("%08d", Link.amount)+";OK;TRANSACTION SUCCESSFUL;"+aquirerCode+";"
										+" ;"+"token;"+cardPan+";"+transacType+";"+authCode+";"+transTime+";"+aquirerId+";"+STAN+";"+progressiveNum+";"
										+actionCode+";"+cardType+";");
							// database.insertData(getCurrentTime(),Integer.parseInt(terminalId), "AdvPayment", "Successful");
						}else{
							output.append(terminalId+";"+String.format("%08d", Link.amount)+";OK;TRANSACTION SUCCESSFUL;"+aquirerCode+";");
							// database.insertData(getCurrentTime(),Integer.parseInt(terminalId), "Payment", "Successful");
						}
						
					}
					else if(message.substring(message.indexOf('E')+1, message.indexOf('E')+3).equalsIgnoreCase("01")){
						String reason4Failure = message.substring(12,36);
					//	database.insertData(getCurrentTime(),Integer.parseInt(terminalId), "Payment", reason4Failure);
						output.append(terminalId+";"+String.format("%08d", Link.amount)+";KO;TRANSACTION UNSUCCESSFUL;"+aquirerCode+";"+reason4Failure+";");
					}else if(message.substring(message.indexOf('E')+1, message.indexOf('E')+3).equalsIgnoreCase("09")){
						output.append("************UNEXPECTED**GT**TAG***");
					}	
				}
				/**checks if the received message is result of DCC transaction**/
				else if(message.contains("0V0")){
					String aquirerCode = "03";
					String terminalId = message.substring(0, 8);
					String cardType = message.substring(47, 48);
					String aquirerId = message.substring(48,59);
					String STAN = message.substring(59,65);
					String progressiveNum = message.substring(65,71);
					String actionCode = message.substring(71,74);
					String amountV = message.substring(74,82);
					String currencyV = message.substring(82,83);
					String conversionRate = message.substring(83,91);
					String currencyCode = message.substring(91,94);
					String transactionAmount = message.substring(94,106);
					String transactionCurrencyDecimal = message.substring(106,107);
					if(message.substring(message.indexOf('V')+1, message.indexOf('V')+3).equalsIgnoreCase("00")){
						
						String cardPan = message.substring(12,31);
						String transacType = message.substring(31,34);
						String authCode = message.substring(34,40);
						String transTime = message.substring(40,47);
						
						if(Link.isAdvance){
							output.append(terminalId+";"+String.format("%08d", Link.amount)+";OK;TRANSACTION SUCCESSFUL;"+aquirerCode+";"
										+" ;"+"token;"+cardPan+";"+transacType+";"+authCode+";"+transTime+";"+aquirerId+";"+STAN+";"+progressiveNum+";"
										+actionCode+";"+cardType+";"+amountV+";"+currencyV+";"+conversionRate+";"+currencyCode+";"+transactionAmount+";"
										+transactionCurrencyDecimal+";");
							//database.insertData(getCurrentTime(),Integer.parseInt(terminalId), "DCC-AdvPayment", "Successful");
						}else{
							output.append(terminalId+";"+String.format("%08d", Link.amount)+";OK;TRANSACTION SUCCESSFUL;"+aquirerCode+";");
							//database.insertData(getCurrentTime(),Integer.parseInt(terminalId), "DCC-Payment", "Successful");
						}
						
					}
					else if(message.substring(message.indexOf('V')+1, message.indexOf('V')+3).equalsIgnoreCase("01")){
						String reason4Failure = message.substring(12,36);
						//database.insertData(getCurrentTime(),Integer.parseInt(terminalId), "DCC-Payment", reason4Failure);
						output.append(terminalId+";"+String.format("%08d", Link.amount)+";KO;TRANSACTION UNSUCCESSFUL;"+aquirerCode+";"+reason4Failure+";");
					}else if(message.substring(message.indexOf('V')+1, message.indexOf('V')+3).equalsIgnoreCase("09")){
						output.append("************UNEXPECTED**GT**TAG***");
					}	
				}
				/**checks if the received message is result of REFUND transaction**/
				else if(message.contains("0A0")){
					String aquirerCode = "03";
					String terminalId = message.substring(0, 8);
					String cardPan = message.substring(12,31);
					String transacType = message.substring(31,34);
					String authCode = message.substring(34,40);
					String aquirerId = message.substring(40,51);
					String transTime = message.substring(51,58);
					if(message.substring(message.indexOf('A')+1, message.indexOf('A')+3).equalsIgnoreCase("00")){
						//database.insertData(getCurrentTime(),Integer.parseInt(terminalId), "Refund", "Successful");
						output.append(terminalId+";"+String.format("%08d", Link.amount)+";OK;TRANSACTION SUCCESSFUL;");
					}else if(message.substring(message.indexOf('A')+1, message.indexOf('A')+3).equalsIgnoreCase("01")){
					//	database.insertData(getCurrentTime(),Integer.parseInt(terminalId), "Refund", "Unsuccessful");
						output.append("********************KO************");
					}else if(message.substring(message.indexOf('A')+1, message.indexOf('A')+3).equalsIgnoreCase("09")){
						output.append("************UNEXPECTED**GT**TAG***");
					}	
				}
				/**checks if the received message is result of TERMINAL STATUS transaction**/
				else if(message.contains("0T0")){
					String terminalId = message.substring(0, 8);
					String totalInEur = message.substring(12,28);
					String actionCode = message.substring(28,31);
					if(message.substring(message.indexOf('T')+1, message.indexOf('T')+3).equalsIgnoreCase("00")){
						if(Link.isTerminalStatus){
							output.append("OK;OK;");
						}else{
							output.append(terminalId+";OK;Successful;"+totalInEur+";"+actionCode+";");
						}
					}else if(message.substring(message.indexOf('T')+1, message.indexOf('T')+3).equalsIgnoreCase("01")){
						output.append(terminalId+";KO;Unsuccessful;"+totalInEur+";"+actionCode+";");
					}else if(message.substring(message.indexOf('T')+1, message.indexOf('T')+3).equalsIgnoreCase("09")){
						output.append("************UNEXPECTED**GT**TAG***");
					}	
				}
				else if(message.contains("0C0")){
					String terminalId = message.substring(0, 8);
					if(message.substring(message.indexOf('C')+1, message.indexOf('C')+3).equalsIgnoreCase("00")){
						String totalInEur = message.substring(12,28);
						String totalInEurRecByHost = message.substring(28,44);
						String actionCode = message.substring(44,47);
						output.append(terminalId+";OK;Successful;"+totalInEur+";"+totalInEurRecByHost+";"+actionCode+";");
					}else if(message.substring(message.indexOf('C')+1, message.indexOf('C')+3).equalsIgnoreCase("01")){
						String failureReason = message.substring(12,31);
						String actionCode = message.substring(31,34);
						output.append(terminalId+";KO;Unsuccessful;"+failureReason+";"+actionCode+";");
					}
				}
				/**checks if the received message is result of DLL transaction**/
				else if(message.contains("0D0")){
					String terminalId = message.substring(0, 8);
					String STAN = message.substring(12,18);
					String progrNum = message.substring(18,24);
					if(message.substring(message.indexOf('D')+1, message.indexOf('D')+3).equalsIgnoreCase("00")){
						String timeData = message.substring(24,31);
						output.append(terminalId+";OK;Successful;"+timeData);
					}else if(message.substring(message.indexOf('D')+1, message.indexOf('D')+3).equalsIgnoreCase("01")){
						String failureReason = message.substring(24,48);
						output.append("KO;Unsuccessful;"+failureReason);
					}
				}
				/**checks if the received message is result of ADDITIONAL DATA FROM GT transaction**/
				else if(message.contains("0U0")){
					int length = Integer.parseInt(message.substring(16,19));
					String AdditionalGtData = message.substring(19,19+length);
					output.append(AdditionalGtData+";");
	
					if(!printOnECR  && Link.isAdvance){
						log.info("generating receipt...!");
						log.trace(output.toString());
						ReceiptJson receipt_Json = new ReceiptJson();
						receipt_Json.setReceipt(output.toString());
						String out = mapper.writeValueAsString(receipt_Json);
						log.trace("JSON -> "+out);
						getContext().getParent().tell(new FinalReceipt(out), getSelf());
						out = null;
						receipt_Json = null;
						output = null;
					}
				}
				
					if((!printOnECR || Link.isTerminalStatus) && !Link.isAdvance){
						log.info("generating receipt...!");
						ReceiptJson receipt_Json = new ReceiptJson();
						receipt_Json.setReceipt(output.toString());
						String out = mapper.writeValueAsString(receipt_Json);
						log.trace("JSON -> "+out);
						getContext().getParent().tell(new FinalReceipt(out), getSelf());
						out = null;
						receipt_Json = null;
						output = null;
					}
				
		}).build();
	}

	/*private void parseReceipt(String receipt){
		String receiptHeader = "";
		String cardBrand = "";
		String transactionType = "";
		String clientAddress1 = "";
		String clientAddress2 = "";
		String merchant = "";
		String Aiic = "";
		String date = "";
		String time = "";
		String TID = "";
		String STAN = "";
		String MODE = "";
		String BankCard = "";
		String AuthCode = "";
		String progOperNum = "";
		String AuthResponseCode = "";
		String PAN = "";
		String ExpDate = "";
		String appId = "";
		String appName = "";
		String appTransactionCounter = "";
		String transCountryCode = "";
		String TransType = "";
		String TransCurrencyCode = "";
		String unpredictableNum = "";
		String tVR = "";
		String appAuthCryptogram = "";
		String issuerAppData = "";
		String appPanSeqNum = "";
		String AMOUNT = "";
		String CURRENCY = "";
		String transStatus = "";
		String verf = "";
		String[] splitted =  Pattern.compile("\n").splitAsStream(receipt).map(s->s.trim()).filter(s->s.trim().length() > 0).collect(Collectors.toList()).toArray(new String[0]);
		for(int i = 0; i< splitted.length; i++ ){
			receiptHeader = splitted[0];
			cardBrand = splitted[1];
			transactionType = splitted[2];
			clientAddress1 = splitted[3];
			clientAddress2 = splitted[4];
			if(splitted[i].contains("Merch.")){
				merchant = splitted[i].substring(6).trim();
			}
			else if(splitted[i].contains("A.I.I.C.")){
				Aiic = splitted[i].substring(8).trim();
			}
			else if(splitted[i].contains("Date")){
				date = splitted[i].substring(4, splitted[i].indexOf("Time")).trim();
				time = splitted[i].substring(splitted[i].indexOf("Time")+4).trim();
			}
			else if(splitted[i].contains("TML")){
				TID = splitted[i].substring(3,splitted[i].indexOf("STAN")).trim();
				STAN = splitted[i].substring(splitted[i].indexOf("STAN")+4).trim();
			}
			else if(splitted[i].contains("Mod.")){
				MODE = splitted[i].substring(4, splitted[i].indexOf("B.C.")).trim();
				BankCard = splitted[i].substring(splitted[i].indexOf("B.C.")+4).trim();
			}
			else if(splitted[i].contains("AUT.")){
				AuthCode = splitted[i].substring(4, splitted[i].contains("OPER.")?splitted[i].indexOf("OPER."):splitted[i].length()-1).trim();
				if(splitted[i].contains("OPER.")){
					progOperNum = splitted[i].substring(splitted[i].indexOf("OPER.")+5).trim();
				}
			}
			else if(splitted[i].contains("AUTH.RESP.CODE")){
				AuthResponseCode = splitted[i].substring(14).trim();	
			}
			else if(splitted[i].contains("PAN SEQ.N")){
				appPanSeqNum = splitted[i].substring(9).trim();
			}
			else if(splitted[i].contains("PAN")){
				PAN = splitted[i].substring(3).trim();	
			}
			else if(splitted[i].contains("EXPIR")){
				ExpDate = splitted[i].substring(5).trim();
			}
			else if(splitted[i].contains("A.ID")){
				appId = splitted[i].substring(4).trim();
			}
			else if(splitted[i].contains("APPL")){
				appName = splitted[i].substring(4).trim();
			}
			else if(splitted[i].contains("ATC")){
				appTransactionCounter = splitted[i].substring(3,splitted[15].indexOf("TCC")).trim();
				transCountryCode = splitted[i].substring(splitted[i].indexOf("TCC")+3, splitted[i].indexOf("TT")).trim();
				TransType = splitted[i].substring(splitted[i].indexOf("TT")+2).trim();
			}
			else if(splitted[i].contains("TrCC")){
				TransCurrencyCode = splitted[i].substring(4, splitted[i].indexOf("UN")).trim();
				unpredictableNum = splitted[i].substring(splitted[i].indexOf("UN")+2).trim();
			}
			else if(splitted[i].contains("TVR")){
				tVR = splitted[i].substring(3).trim();
			}
			else if(splitted[i].contains("T.C.")){
				appAuthCryptogram = splitted[i].substring(4).trim();
			}
			else if(splitted[i].contains("IAD")){
				issuerAppData = splitted[i].substring(3).trim();
			}
			else if(splitted[i].contains("TRANSACTION")){
				transStatus = splitted[i].substring(0, 11)+" "+splitted[i].substring(11);
			}
			else if(splitted[i].contains("AMOUNT")){
				if(cardBrand.contains("DCC")){
					CURRENCY = splitted[i+1].substring(0,3);
					AMOUNT = splitted[i+1].substring(3);
				}else{
					CURRENCY = splitted[i].substring(6,6+3);
					AMOUNT = splitted[i].substring(6+3);
				}
			}
			else if(splitted[i].contains("NO VERIFICATION")||splitted[i].contains("PIN VERIFIED")||splitted[i].contains("SIGNATURE VERIFIED")){
				verf = splitted[i];
			}
			
		}
				
			 System.out.println("receiptheader: "+receiptHeader);
			 System.out.println("cardBrand: "+cardBrand);
			 System.out.println("transType: "+transactionType);
			 System.out.println("clientadd1: "+clientAddress1);
			 System.out.println("clientadd2: "+clientAddress2);
			 System.out.println("merchant: "+merchant);
			 System.out.println("aiic: "+Aiic);
			 System.out.println("date: "+date+" time: "+time);
			 System.out.println("TID: "+TID+" STAN: "+STAN);
			 System.out.println("mODE: "+MODE+" bankCard: "+BankCard);
			 System.out.println("AUTHCODE: "+AuthCode +" operNum: "+progOperNum);
			 System.out.println("authresp: "+AuthResponseCode);
			 System.out.println("pan: "+PAN);
			 System.out.println("expdate: "+ExpDate);
			 System.out.println("appId: "+appId);
			 System.out.println("appname: "+appName);
			 System.out.println("apptranscounter: "+appTransactionCounter + " transCountryCode: "+transCountryCode+" transType: "+TransType);
			 System.out.println("transCurrencyCode: "+TransCurrencyCode +" unpredicNum: "+unpredictableNum);
			 System.out.println("tvr: "+tVR);
			 System.out.println("appauthcryp: "+appAuthCryptogram);
			 System.out.println("AMOUNT: "+ CURRENCY + " "+AMOUNT);
			 System.out.println("issuerappdata: "+issuerAppData);
			 System.out.println("apppanseq: "+appPanSeqNum);
			 System.out.println("Transstatus: "+transStatus);
			 System.out.println("verf: "+verf);
	}*/
	/*private String[] getCurrentTime(){
		LocalDateTime localDateTime = LocalDateTime.now();
		LocalTime localTime = localDateTime.toLocalTime();
		return new String[]{LocalDate.now().toString(),localTime.toString()};
	}*/
	private String lastTransStatus(String receipt){
		if(receipt.contains("TRANSACTION")){
			return receipt.substring(receipt.indexOf("TRANSACTION"),
					receipt.indexOf("TRANSACTION")+20);
		}
		return "";
	}
	
	@Override
	public void postStop() throws Exception {
		log.info("stopping Receipt Generator");
	}
}
