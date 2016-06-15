package ws.wamp.jawampa.examples;

import rx.Subscriber;
import ws.wamp.jawampa.Reply;
import ws.wamp.jawampa.Request;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Santhosh Kumar Tekuri
 */
public class RPCSyncBenchmark{
    static String realm;
    static String url;
    static int callerCount;
    static long warmupDuration;
    static long testDuration;

    static enum State{ warmup, test, stop };
    static AtomicReference<State> curState = new AtomicReference<State>(State.warmup);
    static CountDownLatch latch;
    public static void main(String[] args) throws Exception{
        if(args.length==0){
            System.err.println("args: <realm> <url> <#callers> <warmup-sec> <duration-sec>");
            return;
        }
        realm = args[0];
        url = args[1];
        callerCount = Integer.parseInt(args[2]);
        warmupDuration = Long.parseLong(args[3])*1000;
        testDuration = Long.parseLong(args[4])*1000;

        System.out.printf("{ callers:%d, warmup:%d sec, duration:%d sec}%n", callerCount, warmupDuration / 1000, testDuration / 1000);

        new RPCCallee().start();
        Thread.sleep(5000);
        latch = new CountDownLatch(callerCount+1);
        RPCCaller callers[] = new RPCCaller[callerCount];
        for(int i=0; i<callers.length; i++){
            callers[i] = new RPCCaller();
            callers[i].start();
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
        long calls = 0;
        long latency = 0;
        for(RPCCaller caller : callers){
            calls += caller.calls.get();
            latency += caller.latency.get();
        }

        System.out.println("     calls: "+calls);
        System.out.println("throughput: "+(calls/time)+"/sec");
        System.out.println("   latency: "+(latency/nanos)+" nano");
        System.exit(0);
    }

    static class RPCCaller extends WampClientBootstrap{
        AtomicLong calls = new AtomicLong();
        AtomicLong latency = new AtomicLong();

        public RPCCaller() throws Exception{
            super(RPCSyncBenchmark.realm, RPCSyncBenchmark.url);
        }

        @Override
        protected void onConnect(){
            try{
                latch.countDown();
                latch.await();
                makeCall();
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }

        private void makeCall(){
            if(curState.get()==State.stop)
                return;
            client.call("proc1", System.nanoTime()).subscribe(new Subscriber<Reply>(){
                @Override
                public void onCompleted(){

                }

                @Override
                public void onError(Throwable e){
                    e.printStackTrace();
                }

                @Override
                public void onNext(Reply reply){
                    State state = curState.get();
                    if(state==State.test){
                        calls.incrementAndGet();
                        latency.addAndGet(System.nanoTime() - reply.arguments().get(0).longValue());
                    }
                    makeCall();
                }
            });
        }
    }

    static class RPCCallee extends WampClientBootstrap{
        public RPCCallee() throws Exception{
            super(RPCSyncBenchmark.realm, RPCSyncBenchmark.url);
        }

        @Override
        protected void onConnect(){
            System.out.println("registering proc1");
            client.registerProcedure("proc1").subscribe(new Subscriber<Request>(){
                @Override
                public void onCompleted(){

                }

                @Override
                public void onError(Throwable e){
                    e.printStackTrace();
                }

                @Override
                public void onNext(Request request){
                    request.reply(request.arguments(), request.keywordArguments());
                }
            });
        }
    }
}
