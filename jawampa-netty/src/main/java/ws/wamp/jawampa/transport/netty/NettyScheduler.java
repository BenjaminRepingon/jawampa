package ws.wamp.jawampa.transport.netty;

import io.netty.channel.nio.NioEventLoopGroup;
import ws.wamp.jawampa.connection.IScheduler;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Santhosh Kumar Tekuri
 */
public class NettyScheduler implements IScheduler{
    private NioEventLoopGroup executor =  new NioEventLoopGroup(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "WampClientEventLoop");
        }
    });

    @Override
    public Executor getExecutor(){
        return executor;
    }

    @Override
    public void execute(Runnable runnable){
        executor.execute(runnable);
    }

    final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(20000);
    boolean writing = false;
    final AtomicBoolean writeInProgress = new AtomicBoolean(false);
    Runnable writeTask;

    @Override
    public void submit(Runnable runnable){
        if(executor.next().inEventLoop()){
            executor.submit(runnable);
            return;
        }
        try{
            queue.put(runnable);
        }catch(InterruptedException e){
            e.printStackTrace();
            Thread.currentThread().interrupt();
            return;
        }
        if(writeTask!=null && writeInProgress.compareAndSet(false, true)){
            executor.submit(writeTask);
        }
    }

    @Override
    public void shutdown(){
        executor.shutdownGracefully();
    }
}
