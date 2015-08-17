package ws.wamp.jawampa.examples;

import rx.Subscriber;
import ws.wamp.jawampa.WampClient;
import ws.wamp.jawampa.WampClientBuilder;
import ws.wamp.jawampa.transport.netty.NettyWampClientConnectorProvider;

import java.util.concurrent.TimeUnit;

/**
 * @author Santhosh Kumar Tekuri
 */
public class WampClientBootstrap{
    public final String realm;
    public final String url;
    public final WampClient client;

    public WampClientBootstrap(String realm, String url) throws Exception{
        this.realm = realm;
        this.url = url;

        client = new WampClientBuilder()
                .withConnectorProvider(new NettyWampClientConnectorProvider())
                .withRealm(realm)
                .withUri(url)
                .withInfiniteReconnects()
                .withReconnectInterval(1, TimeUnit.SECONDS).build();

        client.statusChanged().subscribe(new Subscriber<WampClient.State>(){
            @Override
            public void onCompleted(){
                System.out.println("exiting wampclient");
                System.exit(0);
            }

            @Override
            public void onError(Throwable e){
                e.printStackTrace();
            }

            @Override
            public void onNext(WampClient.State state){
                System.out.println("state: "+state);
                if(state instanceof WampClient.ConnectedState)
                    onConnect();
            }
        });
    }

    public void start(){
        client.open();
    }

    protected void onConnect(){}

    public static void main(String[] args){
        try{
            new WampClientBootstrap(args[0], args[1]).start();
        }catch(Throwable thr){
            thr.printStackTrace();
            System.exit(1);
        }
    }
}
