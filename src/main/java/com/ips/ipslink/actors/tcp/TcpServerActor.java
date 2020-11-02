package com.ips.ipslink.actors.tcp;

import java.net.InetSocketAddress;
import java.util.*;
import org.apache.logging.log4j.*;

import com.ips.ipslink.resources.LanguageLoader;

import akka.actor.*;
import akka.io.TcpMessage;
import akka.io.Tcp.*;

public class TcpServerActor  extends AbstractActor {
    private final static Logger log = LogManager.getLogger(TcpServerActor.class); 
    private  InetSocketAddress clientIP;
    final ActorRef manager;
    final HashMap<String, ArrayList<String>> languageDictionary;

    private TcpServerActor(ActorRef manager,InetSocketAddress serverAddress) {
        this.languageDictionary = LanguageLoader.loadLanguages(log);
        this.manager = manager;
        log.trace("starting IPS-Link Server v2.0");
        manager.tell(TcpMessage.bind(getSelf(),serverAddress,100), getSelf());
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
                    final ActorRef handler = getContext().actorOf(TcpConnectionHandlerActor.props(clientIP.getHostString()+":"+clientIP.getPort(),languageDictionary),"handler-"+clientIP.getHostString()+":"+clientIP.getPort());
                    /**
                     * !!NB:
                     * telling the aforesaid akka internal connection actor that the actor "handler"
                     * is the one that shall receive its (the internal actor) messages.
                     */
                    sender().tell(TcpMessage.register(handler), self());

                })
                .build();
    }

}