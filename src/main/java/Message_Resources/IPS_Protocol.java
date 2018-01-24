package Message_Resources;

import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IPS_Protocol {
	@JsonProperty
	private String printFlag ;
	@JsonProperty
	private String messageCode ;
	@JsonProperty
	private String amount;
	@JsonProperty
	private String terminalIp;
	@JsonProperty
	private String statusMessageIp;
	@JsonProperty
	private String GTbit;
	@JsonProperty
	private String statusMessagePort;
	@JsonProperty
	private String terminalPort;
	@JsonProperty
	private String GTmessage;

	public IPS_Protocol() {
	//empty Constructor
	}
	public HashMap<String, String> getParsedMap(){
		final HashMap<String, String> map = new HashMap<String,String>();
		map.put("printFlag", printFlag);
		map.put("messageCode",messageCode);
		map.put("amount",amount);
		map.put("terminalIp",terminalIp);
		map.put("statusMessageIp",statusMessageIp);
		map.put("GTbit",GTbit);
		map.put("statusMessagePort",statusMessagePort);
		map.put("terminalPort",terminalPort);
		map.put("GTmessage",GTmessage);
		return map;
	}
}
