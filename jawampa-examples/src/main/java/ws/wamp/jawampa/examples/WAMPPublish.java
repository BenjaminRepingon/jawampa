package ws.wamp.jawampa.examples;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import rx.Subscriber;
import ws.wamp.jawampa.WampClient;
import ws.wamp.jawampa.WampClientBuilder;
import ws.wamp.jawampa.transport.netty.NettyWampClientConnectorProvider;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Santhosh Kumar Tekuri
 */
public class WAMPPublish{
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

        final CountDownLatch latch = new CountDownLatch(2);
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
                latch.countDown();
            }
        });
        client.open();
        PublishThread thread = new PublishThread(client, topic, latch);
        thread.start();
        latch.await();
        Runtime runtime = Runtime.getRuntime();
        System.out.printf("%5s %8s %10s %6s%n", "Time", "Events", "Throughput", "Memory");
        long prev = System.nanoTime();
        long begin = prev;
        Thread.sleep(interval);
        while(true){
            long cur = System.nanoTime();
            long recvd = thread.requests.getAndSet(0);

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

    static class PublishThread extends Thread{
        private static int count = 0;

        final WampClient client;
        final String topic;
        final CountDownLatch latch;
        final AtomicLong requests = new AtomicLong();
        final AtomicLong replies = new AtomicLong();
        final AtomicLong latencies = new AtomicLong();
        final AtomicLong errors = new AtomicLong();

        public PublishThread(WampClient client, String topic, CountDownLatch latch){
            super("PublishThread"+count++);
            this.client = client;
            this.topic = topic;
            this.latch = latch;
        }

        public void run(){
            latch.countDown();
            try{
                latch.await();
                doRun();
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }

        protected void doRun(){
            ArrayNode arguments = JsonNodeFactory.instance.arrayNode();
            arguments.add("helloworld");
            while(!Thread.interrupted()){
                client.publishMessage(topic, arguments, null);
                requests.incrementAndGet();
            }
        }
    }
}