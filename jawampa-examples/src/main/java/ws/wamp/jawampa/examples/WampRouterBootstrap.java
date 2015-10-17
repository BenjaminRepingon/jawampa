package ws.wamp.jawampa.examples;

import ws.wamp.jawampa.WampRouter;
import ws.wamp.jawampa.WampRouterBuilder;
import ws.wamp.jawampa.transport.netty.SimpleWampWebsocketListener;

import java.net.URI;
import java.util.concurrent.TimeUnit;

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
            System.err.println("args: <realm> <url> <interval>");
            System.exit(0);
        }
        try{
            new WampRouterBootstrap(args[0], args[1]).start();
            long interval = Long.parseLong(args[2]);
            Runtime runtime = Runtime.getRuntime();
            System.out.printf("%n%5s %6s%n", "Time", "Memory");
            long prev = System.nanoTime();
            long begin = prev;
            Thread.sleep(interval);
            while(true){
                long cur = System.nanoTime();

                double usedMemory = (runtime.totalMemory()- runtime.freeMemory())/(1024*1024.0);

                long nano = cur-begin;
                long sec = TimeUnit.NANOSECONDS.toSeconds(nano);
                nano = nano-TimeUnit.SECONDS.toNanos(sec);
                long min = TimeUnit.SECONDS.toMinutes(sec);
                sec = sec-TimeUnit.MINUTES.toSeconds(min);

                System.out.printf("\r%02d:%02d %6.2f", min, sec, usedMemory);
                prev = cur;
                Thread.sleep(interval);
            }
        }catch(Throwable thr){
            thr.printStackTrace();
            System.exit(1);
        }
    }
}
