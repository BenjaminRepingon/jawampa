package ws.wamp.jawampa.examples;

import rx.Subscriber;
import ws.wamp.jawampa.Reply;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Santhosh Kumar Tekuri
 */
public class RPCBenchmark{
    static String realm;
    static String url;
    static int concurrency;
    static int msgPerms;
    static int warmupSec;
    static int durationSec;
    static int times;
    static CountDownLatch latch;
    static AtomicBoolean warmup = new AtomicBoolean(true);

    public static void main(String[] args) throws Exception{
        if(args.length==0){
            System.err.println("args: <realm> <url> <concurrency> <msg-per-ms> <warmup-sec> <duration-sec>");
            System.exit(0);
        }

        realm = args[0];
        url = args[1];
        concurrency = Integer.parseInt(args[2]);
        msgPerms = Integer.parseInt(args[3]);
        warmupSec = Integer.parseInt(args[4]);
        durationSec = Integer.parseInt(args[5]);
        times = durationSec*1000;

        latch = new CountDownLatch(concurrency+1);
        RPCSender senders[] = new RPCSender[concurrency];
        for(int i=0; i<senders.length; i++){
            senders[i] = new RPCSender();
            senders[i].start();
        }
        latch.countDown();
        latch.await();
        System.out.print("warming up...");
        Thread.sleep(warmupSec * 1000);
        System.out.println("done");
        warmup.set(false);
        long begin = System.nanoTime();
        Thread.sleep(durationSec*1000);
        long requests = 0;
        for(RPCSender sender : senders){
            requests += sender.count*msgPerms;
        }

        long latency = 0;
        long replies = 0;
        for(RPCSender sender : senders){
            latency += sender.latency.get();
            replies += sender.replies.get();
        }

        long end = System.nanoTime();
        float time = (float)(end - begin) / TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS);
        double throughput = replies/time;
        System.out.println("      time: "+time+" sec");
        System.out.println("  requests: "+requests);
        System.out.println("   replies: "+replies);
        System.out.println("throughput: "+throughput+"/sec");
        System.out.println("   latency: "+((float)latency/replies)+" sec");
        System.exit(0);
    }

    private static class RPCSender extends WampClientBootstrap implements Runnable{
        private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        public RPCSender() throws Exception{
            super(RPCBenchmark.realm, RPCBenchmark.url);
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

        private AtomicLong replies = new AtomicLong();
        private BlockingQueue<Long> requestTimes = new LinkedBlockingQueue<Long>();
        private AtomicLong latency = new AtomicLong();

        @Override
        public void run(){
            if(!warmup.get())
                ++count;
            for(int i=0; i<msgPerms; i++){
                requestTimes.offer(System.nanoTime());
                client.call("helloworld", "hello").subscribe(new Subscriber<Reply>(){
                    @Override
                    public void onCompleted(){

                    }

                    @Override
                    public void onError(Throwable e){
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Reply reply){
                        Long requestTime = requestTimes.poll();
                        if(!warmup.get()){
                            latency.addAndGet(System.nanoTime()- requestTime);
                            replies.incrementAndGet();
                        }
                    }
                });
            }
            if(count==times)
                self.cancel(false);
        }
    }
}
