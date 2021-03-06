/*
 * Copyright 2015 Matthias Einwag
 *
 * The jawampa authors license this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package ws.wamp.jawampa.connection;

import ws.wamp.jawampa.WampMessages.WampMessage;
import ws.wamp.jawampa.WampSerialization;

public class ClientConnectionController implements IConnectionController
{
	/**
	 * The scheduler on which all state transitions will run
	 */
	private final IScheduler scheduler;

	/**
	 * The wrapped connection object. Must be injected later due to Router design
	 */

	IWampConnection connection;
	/**
	 * The wrapped listener object
	 */
	private final IWampConnectionListener connectionListener;

	/**
	 * Whether to forward incoming messages or not
	 */
	private boolean     forwardIncoming;
	private CloseStatus closeStatus;

	public ClientConnectionController( IScheduler scheduler, IWampConnectionListener connectionListener )
	{
		this.scheduler = scheduler;
		this.connectionListener = connectionListener;
		this.forwardIncoming = true;
		this.closeStatus = CloseStatus.None;
	}

	@Override
	public IWampConnectionListener connectionListener()
	{
		return connectionListener;
	}

	@Override
	public IWampConnection connection()
	{
		return connection;
	}

	@Override
	public void setConnection( IWampConnection connection )
	{
		this.connection = connection;
	}

	// IWampConnection members

	@Override
	public WampSerialization serialization()
	{
		return connection.serialization();
	}

	@Override
	public boolean isSingleWriteOnly()
	{
		return false;
	}

	@Override
	public void sendMessage( WampMessage message, IWampConnectionPromise<Void> promise )
	{
		if ( closeStatus != CloseStatus.None )
			throw new IllegalStateException( "close() was already called" );

		connection.sendMessage( message, promise );
	}

	@Override
	public void close( boolean sendRemaining, IWampConnectionPromise<Void> promise )
	{
		if ( closeStatus != CloseStatus.None )
			throw new IllegalStateException( "close() was already called" );
		// Mark as closed. No other actions allowed after that
		if ( sendRemaining ) closeStatus = CloseStatus.CloseAfterRemaining;
		else closeStatus = CloseStatus.CloseNow;

		// Avoid forwarding of new incoming messages
		forwardIncoming = false;

		// Can immediately start to close
		closeStatus = CloseStatus.CloseSent;
		connection.close( true, promise );
	}

	// IWampConnectionListener methods

	@Override
	public void transportClosed()
	{
		// Avoid forwarding more than once
		if ( !forwardIncoming ) return;
		forwardIncoming = false;

		connectionListener.transportClosed();
	}

	@Override
	public void transportError( final Throwable cause )
	{
		// Avoid forwarding more than once
		if ( !forwardIncoming ) return;
		forwardIncoming = false;

		connectionListener.transportError( cause );
	}

	@Override
	public void messageReceived( final WampMessage message )
	{
		// Drop messages that arrive after close
		if ( !forwardIncoming ) return;

		connectionListener.messageReceived( message );
	}

	@Override
	public void readCompleted()
	{

	}

	@Override
	public void flush()
	{
		connection.flush();
	}
}
