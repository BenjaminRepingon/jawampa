package ws.wamp.jawampa.examples;

import rx.Subscriber;
import ws.wamp.jawampa.Reply;
import ws.wamp.jawampa.Request;
import ws.wamp.jawampa.WampClient;
import ws.wamp.jawampa.client.SessionEstablishedState;
import ws.wamp.jawampa.connection.QueueingConnectionController;
//import ws.wamp.jawampa.connection.SingleThreadedConnectionController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Santhosh Kumar Tekuri
 */
public class MyBenchmark{
    public static Process startJVM(Class mainClass, String... args) throws IOException{
        String javaHome = System.getProperty("java.home");
        String classpath = System.getProperty("java.class.path");
        List<String> cmd = new ArrayList<String>();
        cmd.add(javaHome + "/bin/java");
        cmd.add("-classpath");
        cmd.add(classpath);
        cmd.add(mainClass.getName());
        cmd.addAll(Arrays.asList(args));
        return new ProcessBuilder(cmd).inheritIO().start();
    }

    public static void main(String[] args) throws Exception{
        Process router = startJVM(WampRouterBootstrap.class, "realm", "ws://0.0.0.0:8080/bench");
        Process procedure = startJVM(WampProcedureBootstrap.class, "realm", "ws://localhost:8080/bench");
        new WampInvokerBootstrap("realm", "ws://localhost:8080/bench").start();
        router.waitFor();
    }
}

class WampProcedureBootstrap extends WampClientBootstrap{
    public WampProcedureBootstrap(String realm, String url) throws Exception{
        super(realm, url);
    }

    @Override
    protected void onConnect(){
        System.out.println("registering helloworld");
        client.registerProcedure("helloworld").subscribe(new Subscriber<Request>(){
            @Override
            public void onCompleted(){

            }

            @Override
            public void onError(Throwable e){
                e.printStackTrace();
            }

            @Override
            public void onNext(Request request){
                request.reply("hello world");
            }
        });
    }

    public static void main(String[] args){
        try{
            new WampProcedureBootstrap(args[0], args[1]).start();
        }catch(Throwable thr){
            thr.printStackTrace();
            System.exit(1);
        }
    }
}

class WampInvokerBootstrap extends WampClientBootstrap{
    public WampInvokerBootstrap(String realm, String url) throws Exception{
        super(realm, url);
    }

    private AtomicLong requests = new AtomicLong();
    private AtomicLong replies = new AtomicLong();

    @Override
    protected void onConnect(){
        new Thread(){
            @Override
            public void run(){
                Thread t = new Thread(){
                    @Override
                    public void run(){
                        int count = 0;
                        while(!Thread.interrupted()){
                            ++count;
                            client.call("helloworld").subscribe(new Subscriber<Reply>(){
                                @Override
                                public void onCompleted(){

                                }

                                @Override
                                public void onError(Throwable e){
                                    replies.incrementAndGet();
                                }

                                @Override
                                public void onNext(Reply reply){
                                    replies.incrementAndGet();
                                }
                            });
                        }
                        requests.addAndGet(count);
                    }
                };

                t.start();

                try{
                    t.join(60 * 1000);
                    System.out.println("interrupting");
                    t.interrupt();
                    System.out.println("waiting to join");
                    t.join();
                    int i = 0;
                    while(true){
                        System.out.println(i++ +":-------------------------------");
//                        System.out.println("pending: " + client.pendingCalls());
//                        System.out.println("queued: "+ client.queued());
//                        System.out.println("send: "+ QueueingConnectionController.send);
//                        System.out.println("session-results: "+ SessionEstablishedState.results);
//                        System.out.println("queue-results: "+ QueueingConnectionController.results);
                        System.out.println("requests = " + requests);
                        System.out.println("replies = " + replies);
//                        System.out.println("send: "+ Math.max(SingleThreadedConnectionController.send, QueueingConnectionController.send));
//                        System.out.println("results: "+ SessionEstablishedState.results);

                        if(requests.get()==replies.get())
                          break;
                        Thread.sleep(1000);
                    }
                }catch(InterruptedException e){
                    e.printStackTrace();
                }
            }
        }.start();

    }
}