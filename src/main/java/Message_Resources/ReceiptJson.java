package Message_Resources;

import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReceiptJson {
	@JsonProperty
	private String receipt;
	
	public ReceiptJson() {
		//empty Constructor
		}
	public void setReceipt(String receipt){
		this.receipt = receipt;
	}
	
	protected HashMap<String, String> getParsedMap(){
			final HashMap<String, String> map = new HashMap<String,String>();
			map.put("receiptx", receipt);
			return map;
	}
}
