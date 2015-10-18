package ws.wamp.jawampa.connection;

import java.util.concurrent.Executor;

/**
 * @author Santhosh Kumar Tekuri
 */
public interface IScheduler{
    public Executor getExecutor();
    public void execute(Runnable runnable);
    public void submit(Runnable runnable);
    public void shutdown();
}
