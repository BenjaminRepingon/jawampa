package ws.wamp.jawampa.examples;

import ws.wamp.jawampa.WampRouter;
import ws.wamp.jawampa.WampRouterBuilder;
import ws.wamp.jawampa.transport.netty.SimpleWampWebsocketListener;

import java.net.URI;

/**
 * @author Santhosh Kumar Tekuri
 */
public class WampRouterBootstrap{
    public final String realm;
    public final String url;

    public WampRouterBootstrap(String realm, String url){
        this.realm = realm;
        this.url = url;
    }

    public void start() throws Exception{
        WampRouterBuilder routerBuilder = new WampRouterBuilder();
        WampRouter router;
        routerBuilder.addRealm(realm);
        router = routerBuilder.build();
        URI serverUri = URI.create(url);
        SimpleWampWebsocketListener server;
        server = new SimpleWampWebsocketListener(router, serverUri, null);
        server.start();
        System.out.println("wamprouter started");
    }

    public static void main(String[] args){
        if(args.length==0){
            System.err.println("args: <realm> <url>");
            System.exit(0);
        }
        try{
            new WampRouterBootstrap(args[0], args[1]).start();
        }catch(Throwable thr){
            thr.printStackTrace();
            System.exit(1);
        }
    }
}
