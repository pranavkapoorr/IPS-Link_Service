package Message_Resources;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReceiptJson {
    @JsonProperty
    private String terminalId;
    @JsonProperty
    private String transactionStatus;
    @JsonProperty
    private String aquirerCode;
    @JsonProperty
    private String STAN;
    @JsonProperty
    private String amount;
    @JsonProperty
    private String transactionType;
    @JsonProperty
    private String aquirerId;
    @JsonProperty
    private String cardType;
    @JsonProperty
    private String cardPAN;
    @JsonProperty
    private String actionCode;
    @JsonProperty
    private String progressiveNumber;
    @JsonProperty
    private String authCode;
    @JsonProperty
    private String transactionTime;
    @JsonProperty
    private String hostTotalAmount;
    @JsonProperty
    private String hostTotalAmountReqByHost;
    @JsonProperty
    private String details;
    @JsonProperty
    private String transactionReference;
    @JsonProperty
    private String probeDate;
    @JsonProperty
    private String probeStatus;
    @JsonProperty
    private String DccAmount;
    @JsonProperty
    private String DccCurrency;
    @JsonProperty
    private String DccCurrencyCode;
    @JsonProperty
    private String DccConversionRate;
    @JsonProperty
    private String DccTransactionAmount;
    @JsonProperty
    private String DccTransactionCurrencyDecimal;
   
    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
    }
    public void setTransactionStatus(String transactionStatus) {
        this.transactionStatus = transactionStatus;
    }
    public void setAquirerCode(String aquirerCode) {
        this.aquirerCode = aquirerCode;
    }
    public void setSTAN(String sTAN) {
        STAN = sTAN;
    }
    public void setAmount(String amount) {
        this.amount = amount;
    }
    public void setCardPAN(String cardPAN) {
        this.cardPAN = cardPAN;
    }
    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }
    public void setAquirerId(String aquirerId) {
        this.aquirerId = aquirerId;
    }
    public void setCardType(String cardType) {
        this.cardType = cardType;
    }
    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }
    public void setProgressiveNumber(String progressiveNumber) {
        this.progressiveNumber = progressiveNumber;
    }
    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }
    public void setTransactionTime(String transactionTime) {
        this.transactionTime = transactionTime;
    }
    public void setHostTotalAmount(String hostTotalAmount) {
        this.hostTotalAmount = hostTotalAmount;
    }
    public void setHostTotalAmountReqByHost(String hostTotalAmountReqByHost) {
        this.hostTotalAmountReqByHost = hostTotalAmountReqByHost;
    }
    public void setDetails(String details) {
        this.details = details;
    }
    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }
    public void setProbeStatus(String probeStatus) {
        this.probeStatus = probeStatus;
    }
    public void setProbeDate(String probeDate) {
        this.probeDate = probeDate;
    }
    public void setDccAmount(String dccAmount) {
        DccAmount = dccAmount;
    }
    public void setDccCurrency(String dccCurrency) {
        DccCurrency = dccCurrency;
    }
    public void setDccCurrencyCode(String dccCurrencyCode) {
        DccCurrencyCode = dccCurrencyCode;
    }
    public void setDccConversionRate(String dccConversionRate) {
        DccConversionRate = dccConversionRate;
    }
    public void setDccTransactionAmount(String dccTransactionAmount) {
        DccTransactionAmount = dccTransactionAmount;
    }
    public void setDccTransactionCurrencyDecimal(String dccTransactionCurrencyDecimal) {
        DccTransactionCurrencyDecimal = dccTransactionCurrencyDecimal;
    }

    

    @JsonProperty
	private String receipt;
	
	public ReceiptJson() {
		//empty Constructor
		}
	public void setReceipt(String receipt){
		this.receipt = receipt;
	}
	public String getReceipt(){
        return this.receipt;
    }
	
	/*protected HashMap<String, String> getParsedMap(){
			final HashMap<String, String> map = new HashMap<String,String>();
			map.put("receiptx", receipt);
			return map;
	}*/
}
