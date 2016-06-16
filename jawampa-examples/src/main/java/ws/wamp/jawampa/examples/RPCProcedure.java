package ws.wamp.jawampa.examples;

import rx.Subscriber;
import ws.wamp.jawampa.Request;

/**
 * @author Santhosh Kumar Tekuri
 */
public class RPCProcedure extends WampClientBootstrap{
    public RPCProcedure(String realm, String url) throws Exception{
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
        if(args.length==0){
            System.err.println("args: <realm> <url>");
            System.exit(0);
        }

        try{
            new RPCProcedure(args[0], args[1]).start();
        }catch(Throwable thr){
            thr.printStackTrace();
            System.exit(1);
        }
    }
}
