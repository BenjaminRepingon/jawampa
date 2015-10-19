package ws.wamp.jawampa.examples;

import rx.Subscriber;
import rx.functions.Action1;
import ws.wamp.jawampa.PubSubData;
import ws.wamp.jawampa.WampClient;
import ws.wamp.jawampa.WampClientBuilder;
import ws.wamp.jawampa.transport.netty.NettyWampClientConnectorProvider;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Santhosh Kumar Tekuri
 */
public class WAMPSubscribe{
    public static void main(String[] args) throws Exception{
        if(args.length!=4){
            System.err.println("arguments: <uri> <realm> <topic> <interval>");
            System.exit(1);
        }
        String uri = args[0];
        String realm = args[1];
        final String topic = args[2];
        long interval = Long.parseLong(args[3]);

        final WampClient client = new WampClientBuilder()
                .withConnectorProvider(new NettyWampClientConnectorProvider())
                .withRealm(realm)
                .withUri(uri)
                .withInfiniteReconnects()
                .withReconnectInterval(1, TimeUnit.SECONDS).build();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicLong events = new AtomicLong();
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
                if(!(state instanceof WampClient.ConnectedState))
                    return;
                client.makeSubscription(topic).subscribe(new Action1<PubSubData>(){
                    @Override
                    public void call(PubSubData s){
                        events.incrementAndGet();
                    }
                });
//                client.makeSubscription(topic, String.class).subscribe(new Action1<String>(){
//                    @Override
//                    public void call(String s){
//                        events.incrementAndGet();
//                    }
//                });
                latch.countDown();
            }
        });
        client.open();
        latch.await();
        Runtime runtime = Runtime.getRuntime();
        System.out.printf("%5s %8s %10s %6s%n", "Time", "Events", "Throughput", "Memory");
        long prev = System.nanoTime();
        long begin = prev;
        Thread.sleep(interval);
        while(true){
            long cur = System.nanoTime();
            long recvd = events.getAndSet(0);

            double usedMemory = (runtime.totalMemory()- runtime.freeMemory())/(1024*1024.0);

            long nano = cur-begin;
            long sec = TimeUnit.NANOSECONDS.toSeconds(nano);
            nano = nano-TimeUnit.SECONDS.toNanos(sec);
            long min = TimeUnit.SECONDS.toMinutes(sec);
            sec = sec-TimeUnit.MINUTES.toSeconds(min);

            double duration = ((double)(cur-prev))/ TimeUnit.SECONDS.toNanos(1);
            double throughput = (double)(recvd)/duration;
            System.out.printf("\r%02d:%02d %8d %10.2f %6.2f", min, sec, recvd, throughput, usedMemory);
            prev = cur;
            Thread.sleep(interval);
        }
    }
}
