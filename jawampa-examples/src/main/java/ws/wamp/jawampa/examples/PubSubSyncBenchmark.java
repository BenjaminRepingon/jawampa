package ws.wamp.jawampa.examples;

import rx.Subscriber;
import ws.wamp.jawampa.PubSubData;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Santhosh Kumar Tekuri
 */
public class PubSubSyncBenchmark{
    static String realm;
    static String url;
    static int senderCount;
    static int receiverCount;
    static long warmupDuration;
    static long testDuration;

    static Sender senders[];

    static enum State{ warmup, test, stop };
    static AtomicReference<State> curState = new AtomicReference<State>(State.warmup);
    static CountDownLatch latch;
    public static void main(String[] args) throws Exception{
        if(args.length == 0){
            System.err.println("args: <realm> <url> <#publishers> <#subscriber> <warmup-sec> <duration-sec>");
            return;
        }
        realm = args[0];
        url = args[1];
        senderCount = Integer.parseInt(args[2]);
        receiverCount = Integer.parseInt(args[3]);
        warmupDuration = Long.parseLong(args[4]) * 1000;
        testDuration = Long.parseLong(args[5]) * 1000;

        System.out.printf("{ publishers:%d, subscribers: %d, warmup:%d sec, duration:%d sec}%n", senderCount, receiverCount, warmupDuration / 1000, testDuration / 1000);

        Receiver receivers[] = new Receiver[receiverCount];
        for(int i=0; i<receiverCount; i++){
            receivers[i] = new Receiver();
            receivers[i].start();
        }
        Thread.sleep(10*1000);

        latch = new CountDownLatch(senderCount+1);
        senders = new Sender[senderCount];
        for(int i=0; i<senderCount; i++){
            senders[i] = new Sender(i);
            senders[i].start();
        }
        latch.countDown();
        latch.await();
        System.out.println("warming up.....");
        Thread.sleep(warmupDuration);
        System.out.println("running test....");
        long begin = System.nanoTime();
        curState.set(State.test);
        Thread.sleep(testDuration);
        curState.set(State.stop);
        long end = System.nanoTime();

        long nanos = TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS);
        float time = (float)(end - begin) / nanos;
        long recvd = 0;
        long latency = 0;
        for(Receiver receiver : receivers){
            recvd += receiver.recvd.get();
            latency += receiver.latency.get();
        }

        recvd /= receiverCount;
        latency /= receiverCount;

        System.out.println("     recvd: "+recvd);
        System.out.println("throughput: " + (recvd / time) + "/sec");
        System.out.println("   latency: " + (latency / recvd) + " nanos");
        System.exit(0);

    }

    public static class Sender extends WampClientBootstrap{
        final Integer index;
        AtomicLong pending = new AtomicLong();

        public Sender(int index) throws Exception{
            super(PubSubSyncBenchmark.realm, PubSubSyncBenchmark.url);
            this.index = index;
        }

        @Override
        protected void onConnect(){
            try{
                latch.countDown();
                latch.await();
                publish();
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }

        public void publish(){
            State state = curState.get();
            if(state==State.stop)
                return;
            pending.set(receiverCount);
            client.publish("topic1", index, System.nanoTime());
        }
    }

    public static class Receiver extends WampClientBootstrap{
        AtomicLong recvd = new AtomicLong();
        AtomicLong latency = new AtomicLong();

        public Receiver() throws Exception{
            super(PubSubSyncBenchmark.realm, PubSubSyncBenchmark.url);
        }

        @Override
        protected void onConnect(){
            client.makeSubscription("topic1").subscribe(new Subscriber<PubSubData>(){
                @Override
                public void onCompleted(){

                }

                @Override
                public void onError(Throwable e){
                    e.printStackTrace();
                }

                @Override
                public void onNext(PubSubData pubSubData){
                    Sender sender = senders[pubSubData.arguments().get(0).intValue()];
                    boolean pending = sender.pending.decrementAndGet()!=0;
                    State state = curState.get();
                    if(state==State.test){
                        recvd.incrementAndGet();
                        latency.addAndGet(System.nanoTime()-pubSubData.arguments().get(1).longValue());
                    }
                    if(!pending)
                        sender.publish();
                }
            });
        }
    }
}
