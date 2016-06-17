package ws.wamp.jawampa.connection;

import java.util.concurrent.Executor;

/**
 * @author Santhosh Kumar Tekuri
 */
public interface IScheduler
{
	Executor getExecutor();

	boolean isEventThread();

	void execute( Runnable runnable );

	void submit( Runnable runnable );

	void shutdown();

	boolean isShutdown();
}
