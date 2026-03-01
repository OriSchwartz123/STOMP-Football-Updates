package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConnectionsImpl<T>  implements Connections<T>{

    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Integer>> channels = new ConcurrentHashMap<>();
    @Override
    public boolean send(int connectionId, T msg){
        ConnectionHandler<T> handler = clients.get(Integer.valueOf(connectionId));
        if(handler != null){
            handler.send(msg);
            return true;
        }
        return false;

    }
    @Override
    public void send(String channel, T msg){
        CopyOnWriteArrayList<Integer> channelToSend = channels.get(channel);
        if (channelToSend != null){
            for(Integer connectionId: channelToSend){
                send(connectionId, msg);
            }
        }
    }
    @Override
    public void disconnect(int connectionId){
        clients.remove(Integer.valueOf(connectionId));
        for(CopyOnWriteArrayList<Integer> channel: channels.values()){
            channel.remove(Integer.valueOf(connectionId));
        }
    }

    public void connect(int connectionId,ConnectionHandler<T> handler){
        if(handler!=null){
            clients.put(Integer.valueOf(connectionId),handler);
        }
    }

    public void subscribe(String channel, int connectionId){
        channels.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).addIfAbsent(Integer.valueOf(connectionId));
    }

    public boolean unSubscribe(String channel, int connectionId){
        CopyOnWriteArrayList<Integer> channelToUnsubscribe = channels.get(channel);
        if (channelToUnsubscribe != null){
            channelToUnsubscribe.remove(Integer.valueOf(connectionId));
            return true;
        }
        return false;
    }
}
