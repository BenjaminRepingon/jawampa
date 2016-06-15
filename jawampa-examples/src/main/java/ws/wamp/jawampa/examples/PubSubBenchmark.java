package ws.wamp.jawampa.examples;

import rx.Subscriber;
import ws.wamp.jawampa.PubSubData;
import ws.wamp.jawampa.Reply;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Santhosh Kumar Tekuri
 */
public class PubSubBenchmark{
    static String realm;
    static String url;
    static int publisherCount;
    static int subscriberCount;
    static int msgPerms;
    static int warmupSec;
    static int durationSec;
    static int times;
    static CountDownLatch latch;
    static AtomicBoolean warmup = new AtomicBoolean(true);
    static MessageSubscriber subscribers[];

    public static void main(String[] args) throws Exception{
        if(args.length==0){
            System.err.println("args: <realm> <url> <#publishers> <#subscribers> <msg-per-ms> <warmup-sec> <duration-sec>");
            System.exit(0);
        }
        realm = args[0];
        url = args[1];
        publisherCount = Integer.parseInt(args[2]);
        subscriberCount = Integer.parseInt(args[3]);
        msgPerms = Integer.parseInt(args[4]);
        warmupSec = Integer.parseInt(args[5]);
        durationSec = Integer.parseInt(args[6]);
        times = durationSec*1000;

        subscribers = new MessageSubscriber[subscriberCount];
        for(int i=0; i<subscriberCount; i++){
            subscribers[i] = new MessageSubscriber();
            subscribers[i].start();
        }
        Thread.sleep(10*1000); // let subscribers connect
        latch = new CountDownLatch(publisherCount+1);
        MessagePublisher publishers[] = new MessagePublisher[publisherCount];
        for(int i=0; i<publisherCount; i++){
            publishers[i] = new MessagePublisher();
            publishers[i].start();
        }
        latch.countDown();
        latch.await();
        System.out.print("warming up...");
        Thread.sleep(warmupSec * 1000);
        System.out.println("done");
        warmup.set(false);
        long begin = System.nanoTime();
        Thread.sleep(durationSec*1000);
        long published = 0;
        for(MessagePublisher publisher : publishers){
            published += publisher.count*msgPerms;
        }

        long end = System.nanoTime();
        float time = (float)(end - begin) / TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS);
        System.out.println("      time: "+time+" sec");
        System.out.println(" published: "+published);
        for(int i=0; i<subscriberCount; i++){
            long latency = subscribers[i].latency.get();
            long recvd = subscribers[i].recvd.get();
            double throughput = recvd/time;
            System.out.println("subscriber["+i+"] ------------------------------");
            System.out.println("     recvd: "+recvd);
            System.out.println("throughput: "+throughput+"/sec");
            System.out.println("   latency: "+((float)latency/recvd)+" nanos");
        }
        System.exit(0);
    }

    static class MessagePublisher extends WampClientBootstrap implements Runnable{
        private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        public MessagePublisher() throws Exception{
            super(PubSubBenchmark.realm, PubSubBenchmark.url);
        }

        ScheduledFuture<?> self;
        @Override
        protected void onConnect(){
            try{
                latch.countDown();
                latch.await();
                self = executor.scheduleAtFixedRate(this, 0, 1, TimeUnit.MILLISECONDS);
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }

        int count = 0;

        @Override
        public void run(){
            if(!warmup.get())
                ++count;
            for(int i=0; i<msgPerms; i++){
                long time = System.nanoTime();
                for(MessageSubscriber subscriber : subscribers){
                    subscriber.requestTimes.offer(time);
                }
                client.publish("mytopic", "hello");
            }
            if(count==times)
                self.cancel(false);
        }
    }

    static class MessageSubscriber extends WampClientBootstrap{
        public MessageSubscriber() throws Exception{
            super(PubSubBenchmark.realm, PubSubBenchmark.url);
        }

        private BlockingQueue<Long> requestTimes = new LinkedBlockingQueue<Long>();
        AtomicLong recvd = new AtomicLong();
        AtomicLong latency = new AtomicLong();

        @Override
        protected void onConnect(){
            System.out.println("subscriber connected");
            client.makeSubscription("mytopic").subscribe(new Subscriber<PubSubData>(){
                @Override
                public void onCompleted(){

                }

                @Override
                public void onError(Throwable e){
                    e.printStackTrace();
                }

                @Override
                public void onNext(PubSubData pubSubData){
                    Long requestTime = requestTimes.poll();
                    if(!warmup.get()){
                        latency.addAndGet(System.nanoTime()- requestTime);
                        recvd.incrementAndGet();
                    }
                }
            });
        }
    }
}
