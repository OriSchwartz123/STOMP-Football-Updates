package bgu.spl.net.impl.stomp;
import bgu.spl.net.srv.Server;
import bgu.spl.net.api.StompMessagingProtocolAdapter;
import bgu.spl.net.api.StompMessagingProtocolImpl;

import bgu.spl.net.api.MessageEncoderDecoderImpl;
public class StompServer {

    public static void main(String[] args) {
        
        if (args.length < 2) {
            System.out.println("Usage: StompServer <port> <server_type>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String serveType = args[1];
        
        if(serveType.equalsIgnoreCase("tpc")){
            Server.threadPerClient(
                port,
                () -> new StompMessagingProtocolAdapter<>(new StompMessagingProtocolImpl()),
                () -> new MessageEncoderDecoderImpl()
        ).serve();
        }else if(serveType.equalsIgnoreCase("reactor")){
            Server.reactor(
                5,
                port,
                () -> new StompMessagingProtocolAdapter<>(new StompMessagingProtocolImpl()),
                () -> new MessageEncoderDecoderImpl()
        ).serve();
        }
    }
}
