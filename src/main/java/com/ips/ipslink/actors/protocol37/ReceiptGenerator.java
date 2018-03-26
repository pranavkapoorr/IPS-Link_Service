package com.ips.ipslink.actors.protocol37;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ips.ipslink.actormessages.FinalReceipt;
import com.ips.ipslink.actors.convertor.Link;
import com.ips.ipslink.marshallers.ReceiptJson;

import akka.actor.AbstractActor;
import akka.actor.Props;

public class ReceiptGenerator extends AbstractActor{
	private final static Logger log = LogManager.getLogger(ReceiptGenerator.class);
	private StringBuffer receiptBuffer = new StringBuffer();
	StringBuilder receipt = new StringBuilder();
	private final ObjectMapper mapper;
	private static char newLine = (char)10;
	private final boolean  printOnECR;
	private ReceiptJson receipt_Json;
	
	public static Props props(boolean printOnECR){
		return Props.create(ReceiptGenerator.class , printOnECR);
	}
	private ReceiptGenerator(boolean printOnECR) {
		this.printOnECR = printOnECR;
		this.mapper = new ObjectMapper();
		this.mapper.setSerializationInclusion(Include.NON_NULL);
		this.receipt_Json = new ReceiptJson();
	}
	
	@Override
	public void preStart() throws Exception {
	log.info(getSelf().path().name()+" starting Receipt Generator");
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
							receipt.insert(i,newLine);//"\n");
						}
						receipt_Json.setReceipt(receipt.toString());
						/**sends out the receipt if printOnECR is enabled ie no S message will be expected but U message will be if GT bit is on**/
						if(printOnECR ){
						    getSelf().tell(receipt_Json, getSelf());
						}
					}
					
					
				}
				/**checks if the received message is result of reversal, payment etc**/
				else if(message.contains("0E0")){
					String terminalId = message.substring(0, 8);
					String cardType = message.substring(47, 48);
					String aquirerId = message.substring(48,59);
					String STAN = message.substring(59,65);
					String progressiveNum = message.substring(65,71);
					String actionCode = message.substring(71,74);
					receipt_Json.setTerminalId(terminalId);
             
                    receipt_Json.setCardType(cardType);
                    receipt_Json.setAquirerId(aquirerId);
                    receipt_Json.setSTAN(STAN);
                    receipt_Json.setActionCode(actionCode);
                    receipt_Json.setProgressiveNumber(progressiveNum);
                    receipt_Json.setAmount(String.valueOf(Link.amount));
					if(message.substring(message.indexOf('E')+1, message.indexOf('E')+3).equalsIgnoreCase("00")){
						
						String cardPan = message.substring(12,31);
						String transacType = message.substring(31,34);
						String authCode = message.substring(34,40);
						String transTime = message.substring(40,47);
						receipt_Json.setTransactionStatus("OK");
						receipt_Json.setTransactionStatusText("Transaction Successful");
						receipt_Json.setCardPAN(cardPan);
						receipt_Json.setTransactionType(transacType);
						receipt_Json.setAuthCode(authCode);
						receipt_Json.setTransactionDate(getCurrentDate());
		                receipt_Json.setTransactionTime(getCurrentTime());
				}
					else if(message.substring(message.indexOf('E')+1, message.indexOf('E')+3).equalsIgnoreCase("01")){
						String reason4Failure = message.substring(12,36);
						receipt_Json.setTransactionStatus("KO");
						receipt_Json.setTransactionStatusText(reason4Failure);
					}else if(message.substring(message.indexOf('E')+1, message.indexOf('E')+3).equalsIgnoreCase("09")){
					    receipt_Json.setTransactionStatus("KO");
					    receipt_Json.setTransactionStatusText("***unexpected***");
					}	
				}
				/**checks if the received message is result of DCC transaction**/
				else if(message.contains("0V0")){
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
					receipt_Json.setTerminalId(terminalId);
					receipt_Json.setAmount(String.valueOf(Link.amount));
                    receipt_Json.setCardType(cardType);
                    receipt_Json.setAquirerId(aquirerId);
                    receipt_Json.setSTAN(STAN);
                    receipt_Json.setActionCode(actionCode);
                    receipt_Json.setProgressiveNumber(progressiveNum);
                    receipt_Json.setDccAmount(amountV);
                    receipt_Json.setDccCurrency(currencyV);
                    receipt_Json.setDccCurrencyCode(currencyCode);
                    receipt_Json.setDccConversionRate(conversionRate);
                    receipt_Json.setDccTransactionAmount(transactionAmount);
                    receipt_Json.setDccTransactionCurrencyDecimal(transactionCurrencyDecimal);
					if(message.substring(message.indexOf('V')+1, message.indexOf('V')+3).equalsIgnoreCase("00")){	
						String cardPan = message.substring(12,31);
						String transacType = message.substring(31,34);
						String authCode = message.substring(34,40);
						String transTime = message.substring(40,47);
						receipt_Json.setTransactionStatus("OK");
                        receipt_Json.setTransactionStatusText("Transaction Successful");
						receipt_Json.setCardPAN(cardPan);
                        receipt_Json.setTransactionType(transacType);
                        receipt_Json.setAuthCode(authCode);
                        receipt_Json.setTransactionDate(getCurrentDate());
                        receipt_Json.setTransactionTime(getCurrentTime());
					}else if(message.substring(message.indexOf('V')+1, message.indexOf('V')+3).equalsIgnoreCase("01")){
						String reason4Failure = message.substring(12,36);
						receipt_Json.setTransactionStatus("KO");
						receipt_Json.setTransactionStatusText(reason4Failure);
					}else if(message.substring(message.indexOf('V')+1, message.indexOf('V')+3).equalsIgnoreCase("09")){
					    receipt_Json.setTransactionStatus("KO");
					    receipt_Json.setTransactionStatusText("**UNEXPECTED TAG**");
					}	
				}
				/**checks if the received message is result of REFUND transaction**/
				else if(message.contains("0A0")){
					String terminalId = message.substring(0, 8);
					String cardPan = message.substring(12,31);
					String transacType = message.substring(31,34);
					String authCode = message.substring(34,40);
					String aquirerId = message.substring(40,51);
					String transTime = message.substring(51,58);
					receipt_Json.setTerminalId(terminalId);
					receipt_Json.setAmount(String.valueOf(Link.amount));
					receipt_Json.setCardPAN(cardPan);
					receipt_Json.setTransactionType(transacType);
					receipt_Json.setAuthCode(authCode);
					receipt_Json.setAquirerId(aquirerId);
					if(message.substring(message.indexOf('A')+1, message.indexOf('A')+3).equalsIgnoreCase("00")){
					    receipt_Json.setTransactionStatus("OK");
                        receipt_Json.setTransactionStatusText("Transaction Successful");
					    receipt_Json.setTransactionDate(getCurrentDate());
	                    receipt_Json.setTransactionTime(getCurrentTime());
					}else if(message.substring(message.indexOf('A')+1, message.indexOf('A')+3).equalsIgnoreCase("01")){
					    receipt_Json.setTransactionStatus("KO");
					}else if(message.substring(message.indexOf('A')+1, message.indexOf('A')+3).equalsIgnoreCase("09")){
					    receipt_Json.setTransactionStatus("KO");
					    receipt_Json.setTransactionStatusText("**UNEXPECTED TAG**");
					}	
				}
				/**checks if the received message is result of TERMINAL STATUS transaction**/
				else if(message.contains("0T0")){
					String terminalId = message.substring(0, 8);
					String totalInEur = message.substring(12,28);
					String actionCode = message.substring(28,31);
					if(message.substring(message.indexOf('T')+1, message.indexOf('T')+3).equalsIgnoreCase("00")){
						if(Link.isTerminalStatus){
						    receipt_Json.setReceipt("OK;Successful");
						}else{
							receipt_Json.setTerminalId(terminalId);
							receipt_Json.setTransactionStatus("OK");
	                        receipt_Json.setTransactionStatusText("Transaction Successful");
							receipt_Json.setHostTotalAmount(totalInEur);
							receipt_Json.setActionCode(actionCode);
						}
					}else if(message.substring(message.indexOf('T')+1, message.indexOf('T')+3).equalsIgnoreCase("01")){
					    receipt_Json.setTerminalId(terminalId);
					    receipt_Json.setTransactionStatus("KO");
                        receipt_Json.setTransactionStatusText("Transaction Unsuccessful");
                        receipt_Json.setHostTotalAmount(totalInEur);
                        receipt_Json.setActionCode(actionCode);
					}else if(message.substring(message.indexOf('T')+1, message.indexOf('T')+3).equalsIgnoreCase("09")){
					    receipt_Json.setTerminalId(terminalId);
                        receipt_Json.setTransactionStatus("KO");
                        receipt_Json.setTransactionStatusText("**UNEXPECTED TAG**");
                        receipt_Json.setHostTotalAmount(totalInEur);
                        receipt_Json.setActionCode(actionCode);
					}	
				}
				else if(message.contains("0C0")){
					String terminalId = message.substring(0, 8);
					if(message.substring(message.indexOf('C')+1, message.indexOf('C')+3).equalsIgnoreCase("00")){
						String totalInEur = message.substring(12,28);
						String totalInEurRecByHost = message.substring(28,44);
						String actionCode = message.substring(44,47);
						receipt_Json.setTerminalId(terminalId);
						receipt_Json.setTransactionStatus("OK");
                        receipt_Json.setTransactionStatusText("Transaction Successful");
                        receipt_Json.setHostTotalAmount(totalInEur);
                        receipt_Json.setHostTotalAmountReqByHost(totalInEurRecByHost);
                        receipt_Json.setActionCode(actionCode);
					}else if(message.substring(message.indexOf('C')+1, message.indexOf('C')+3).equalsIgnoreCase("01")){
						String failureReason = message.substring(12,31);
						String actionCode = message.substring(31,34);
						 receipt_Json.setTerminalId(terminalId);
						 receipt_Json.setTransactionStatus("KO");
	                     receipt_Json.setActionCode(actionCode);
	                     receipt_Json.setTransactionStatusText(failureReason);
					}
				}
				/**checks if the received message is result of DLL transaction**/
				else if(message.contains("0D0")){
					String terminalId = message.substring(0, 8);
					String STAN = message.substring(12,18);
					String progrNum = message.substring(18,24);
					receipt_Json.setTerminalId(terminalId);
					receipt_Json.setSTAN(STAN);
					receipt_Json.setProgressiveNumber(progrNum);
					if(message.substring(message.indexOf('D')+1, message.indexOf('D')+3).equalsIgnoreCase("00")){
						//String timeData = message.substring(24,31);
					    receipt_Json.setTransactionStatus("OK");
                        receipt_Json.setTransactionStatusText("Transaction Successful");
						//receipt_Json.setTransactionStatusText(timeData);
					}else if(message.substring(message.indexOf('D')+1, message.indexOf('D')+3).equalsIgnoreCase("01")){
						String failureReason = message.substring(24,48);
						receipt_Json.setTransactionStatus("KO");
						receipt_Json.setTransactionStatusText(failureReason);
					}
				}
				/**checks if the received message is result of ProbePed transaction**/
                else if(message.contains("0s0")){
                    String terminalId = message.substring(0,8);
                    String date = message.substring(20,30);
                    String status = message.substring(30,31);
                    String data = message.substring(31);
                    receipt_Json.setTerminalId(terminalId);
                    receipt_Json.setTransactionStatus("OK");
                    receipt_Json.setTransactionStatusText("Transaction Successful");
                    receipt_Json.setPedDate(date.substring(0,6));
                    receipt_Json.setPedTime(date.substring(6));
                    receipt_Json.setPedStatus(status);
                    receipt_Json.setFirmwareVersion(data.substring(data.indexOf("EMV")+3,data.indexOf("EMV")+3+4));
                    receipt_Json.setPartNumber(data.substring(data.indexOf("P/N")+3,data.indexOf("P/N")+3+6));
                    receipt_Json.setSerialNumber(data.substring(data.indexOf("S/N")+3));
                }
				/**checks if the received message is result of ADDITIONAL DATA FROM GT transaction**/
				else if(message.contains("0U0")){
					int length = Integer.parseInt(message.substring(16,19));
					String AdditionalGtData = message.substring(19,19+length);
					receipt_Json.setCardPresentToken(AdditionalGtData.substring(0, 19));
					if(length>21){
    					receipt_Json.setOmniChannelToken(AdditionalGtData.substring(20, 110));
    					receipt_Json.setOmniChannelGUID(AdditionalGtData.substring(111));
					}
	
					if(!printOnECR  && Link.isAdvance){
					    getSelf().tell(receipt_Json, getSelf());
					}
				}
				
					if((!printOnECR || Link.isTerminalStatus) && !Link.isAdvance){
					    getSelf().tell(receipt_Json, getSelf());
					}
				
		}).match(ReceiptJson.class, receiptX->{
		            if(Link.wait4CardRemoval){
		                if(Link.cardRemoved){
		                    generateJsonReceipt(receiptX);
		                }else{
		                    getSelf().tell(receiptX, getSelf());
		                }
		            }else{
		                generateJsonReceipt(receiptX);
		            }
		        })
		 .build();
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
	private String getCurrentTime(){
		LocalDateTime localDateTime = LocalDateTime.now();
		LocalTime localTime = localDateTime.toLocalTime();
		return localTime.format(DateTimeFormatter.ofPattern("HHmm")).toString();
	}
	private String getCurrentDate(){
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")).toString();
    }
	private ReceiptJson lastTransStatus(String receipt){
	    ReceiptJson tempJson = new ReceiptJson();
	    String[] receiptLines = receipt.split(String.valueOf(newLine));
	    
	   for(int i=0;i<receiptLines.length;i++){
	       if(receiptLines[i].contains("STAN")){
	           tempJson.setSTAN(receiptLines[i].substring(receiptLines[i].indexOf("STAN")+"STAN".length()).trim());
	       }else if(receiptLines[i].contains("PAN  ")){
              tempJson.setCardPAN(receiptLines[i].substring(receiptLines[i].indexOf("PAN")+"PAN".length()).trim());
           }else if(receiptLines[i].contains("AMOUNT:")){
               tempJson.setAmount(receiptLines[i+1].trim());
           }
	       else if(receiptLines[i].contains("AMOUNT")){
	           tempJson.setAmount(receiptLines[i].substring(receiptLines[i].indexOf("AMOUNT")+"AMOUNT".length()).trim());
	       }else if(receiptLines[i].contains("TRANSACTION")){
               tempJson.setTransactionStatusText(receiptLines[i].substring(receiptLines[i].indexOf("TRANSACTION"),receiptLines[i].indexOf("TRANSACTION")+20));
               if(receiptLines[i].substring(receiptLines[i].indexOf("TRANSACTION"),receiptLines[i].indexOf("TRANSACTION")+20).contains("APPROVED")){
                   tempJson.setTransactionStatus("OK");
               }else{
                   tempJson.setTransactionStatus("KO");
               }
           }
	   }
	   return tempJson;
	}
	private void generateJsonReceipt(ReceiptJson receiptX) throws JsonProcessingException{
        log.info(getSelf().path().name()+" generating receipt...!");
        if(Link.isLastTransStatus){
             receiptX = lastTransStatus(receiptX.getReceipt()); 
        }
        String out = mapper.writeValueAsString(receiptX);
        log.trace(getSelf().path().name()+" JSON -> "+out);
        getContext().getParent().tell(new FinalReceipt(out), getSelf()); ///writing out the receipt
    out = null;
    receiptX = null;
    }
	
	@Override
	public void postStop() throws Exception {
		log.info(getSelf().path().name()+" stopping Receipt Generator");
	}
}
