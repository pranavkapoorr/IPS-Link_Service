package core.tcp;

import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.io.TcpMessage;
import akka.io.Tcp.Bound;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.Connected;

public class TcpServerActor  extends AbstractActor {
	private final static Logger log = LogManager.getLogger(TcpServerActor.class); 
	private  InetSocketAddress clientIP;
	public static volatile int clientnum = 0;
	//private final ConnectionVault vault; //to have a track of connections
	final ActorRef manager;
	 private TcpServerActor(ActorRef manager,InetSocketAddress serverAddress) {
	        this.manager = manager;
	        	log.trace("starting TCP Server");
	        	manager.tell(TcpMessage.bind(getSelf(),serverAddress,100), getSelf());
	       // 	vault = new ConnectionVault();
	    }

	  
	  
	  public static Props props(ActorRef tcpMnager, InetSocketAddress serverAddress) {
	    return Props.create(TcpServerActor.class, tcpMnager, serverAddress);
	  }


	  @Override
	  public Receive createReceive() {
	    return receiveBuilder()
	    	.match(Bound.class, msg -> {
	    		 log.trace("Server Status: "+msg);

	      })
	      .match(CommandFailed.class, msg -> {
	    	  log.error(msg);
	    	  getContext().stop(getSelf());
	      
	      })
	      .match(Connected.class, conn -> {
	    	  log.trace("Server :"+conn);
	    	  clientIP = conn.remoteAddress();
	    	// if(addToVaultAndCheck(clientIP)){//for limiting incoming connections
	        //  vault.getVault().forEach((k,v)->System.err.print("IP:"+k+"  value:"+v));
	    	  final ActorRef handler = getContext().actorOf(TcpConnectionHandlerActor.props(clientIP),"handler"+clientIP.getHostString()+":"+clientIP.getPort());
	                /**
	                 * !!NB:
	                 * telling the aforesaid akka internal connection actor that the actor "handler"
	                 * is the one that shall receive its (the internal actor) messages.
	                 */
	                sender().tell(TcpMessage.register(handler), self());
	    	// }
	      })
	      .match(InetSocketAddress.class, clientIp->{
	    //	  removeFromVault(clientIp);
	      })
	      .build();
	  }
	/* private void removeFromVault(InetSocketAddress clientIp){
		 if(vault.getVault().containsKey(clientIp.getHostString())){
       	  int oldValue = vault.getVault().get(clientIp.getHostString());
       	  vault.getVault().replace(clientIp.getHostString(), oldValue-1);
         }
	 }
	 private boolean addToVaultAndCheck(InetSocketAddress clientIp){
		 boolean result = false;
		 if(vault.getVault().containsKey(clientIp.getHostString())){
       	  int oldValue = vault.getVault().get(clientIp.getHostString());
       	  if(oldValue >= 9){
       		System.err.println("errrorrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr");  
       	  }else{
       		  result = true;
       		  vault.getVault().replace(clientIp.getHostString(), oldValue+1);
       	  	}
       	  }else{
       	  vault.getVault().put(clientIp.getHostString(), 1);
         }
		 return result;
	 }*/
}