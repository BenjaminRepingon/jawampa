package ws.wamp.jawampa.connection;

/**
 * A lightweight future type which is used for the implementation
 * of WAMP connection adapters.
 */
public interface IWampConnectionFuture<T>
{
	/**
	 * @return Whether the operation completed with success or an error
	 */
	boolean isSuccess();

	/**
	 * @return The result of the operation (if the operation completed with success)
	 */
	T result();

	/**
	 * @return An error reason (if the operation completed with an error
	 */
	Throwable error();

	/**
	 * @return An arbitrary state object which is stored inside the future
	 */
	Object state();
}