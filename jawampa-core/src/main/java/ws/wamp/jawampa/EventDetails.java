package ws.wamp.jawampa;

public class EventDetails<T>
{
	private final T      message;
	private final String topic;

	public EventDetails( T msg, String topic )
	{
		this.message = msg;
		this.topic = topic;
	}

	public T message()
	{
		return message;
	}

	public String topic()
	{
		return topic;
	}
}
