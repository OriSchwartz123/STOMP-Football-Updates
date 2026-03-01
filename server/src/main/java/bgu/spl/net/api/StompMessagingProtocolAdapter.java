package bgu.spl.net.api;

public class StompMessagingProtocolAdapter<T> implements MessagingProtocol<String> {
    final private StompMessagingProtocolImpl protocol;
    
    public StompMessagingProtocolAdapter(StompMessagingProtocolImpl givenProtocol){
        protocol = givenProtocol;
    }

    @Override
    public String process(String msg) {
        protocol.process(msg);
        return null;
    }

    @Override
    public boolean shouldTerminate() {
        return protocol.shouldTerminate();
    }
    
    public StompMessagingProtocolImpl getStompProtocol() {
        return protocol;
    }

}
