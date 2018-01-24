package Message_Resources;

public class GT37Message{
	private final byte[] message;
	public GT37Message(byte[] message) {
		this.message= message;
	}
	public byte[] getMessage(){
		return message;
	}
}
