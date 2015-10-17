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

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

public class ServerConnectionController implements IConnectionController {

    /** Possible states while closing the connection */
    enum CloseStatus {
        /** Close was not issued */
        None,
        /** Connection should be closed at the next possible point of time */
        CloseNow,
        /** Connection should be closed after all already queued messages have been sent */
        CloseAfterRemaining,
        /** Close was issued but not yet acknowledged */
        CloseSent,
        /** Close is acknowledged */
        Closed
    }

    /** The scheduler on which all state transitions will run */
    final ScheduledExecutorService scheduler;
    /** The wrapped connection object. Must be injected later due to Router design */
    IWampConnection connection;
    /** The wrapped listener object */
    final IWampConnectionListener connectionListener;

    /** Whether to forward incoming messages or not */
    boolean forwardIncoming = true;
    CloseStatus closeStatus = CloseStatus.None;

    private List<IWampConnection> flushList;

    public ServerConnectionController(ScheduledExecutorService scheduler,
                                      IWampConnectionListener connectionListener, List<IWampConnection> flushList) {
        this.scheduler = scheduler;
        this.connectionListener = connectionListener;
        this.flushList = flushList;
    }
    
    @Override
    public IWampConnectionListener connectionListener() {
        return connectionListener;
    }
    
    @Override
    public IWampConnection connection() {
        return connection;
    }
    
    @Override
    public void setConnection(IWampConnection connection) {
        this.connection = connection;
    }
    
    /**
     * Tries to schedule a runnable on the underlying executor.<br>
     * Rejected executions will be suppressed.<br>
     * This is useful for cases when the clients EventLoop is shut down before
     * the EventLoop of the underlying connection.
     * 
     * @param action The action to schedule.
     */
    private void tryScheduleAction(Runnable action) {
        try {
            scheduler.submit(action);
        } catch (RejectedExecutionException e) {}
    }
    
    // IWampConnection members 
    
    @Override
    public WampSerialization serialization() {
        return connection.serialization();
    }

    @Override
    public boolean isSingleWriteOnly() {
        return false;
    }

    @Override
    public void sendMessage(WampMessage message, IWampConnectionPromise<Void> promise) {
        if (closeStatus != CloseStatus.None)
            throw new IllegalStateException("close() was already called");

        connection.sendMessage(message, promise);
        if(!flushList.contains(connection))
            flushList.add(connection);
    }

    @Override
    public void close(boolean sendRemaining, IWampConnectionPromise<Void> promise) {
        if (closeStatus != CloseStatus.None)
            throw new IllegalStateException("close() was already called");
        // Mark as closed. No other actions allowed after that
        if (sendRemaining) closeStatus = CloseStatus.CloseAfterRemaining;
        else closeStatus = CloseStatus.CloseNow;

        // Avoid forwarding of new incoming messages
        forwardIncoming = false;

        closeStatus = CloseStatus.CloseSent;
        connection.close(true, promise);
    }
    
    // IWampConnectionListener methods

    @Override
    public void transportClosed() {
        tryScheduleAction(new Runnable() {
            @Override
            public void run() {
                // Avoid forwarding more than once
                if (!forwardIncoming) return;
                forwardIncoming = false;
                
                connectionListener.transportClosed();
            }
        });
    }

    @Override
    public void transportError(final Throwable cause) {
        tryScheduleAction(new Runnable() {
            @Override
            public void run() {
                // Avoid forwarding more than once
                if (!forwardIncoming) return;
                forwardIncoming = false;
                
                connectionListener.transportError(cause);
            }
        });
    }

    @Override
    public void messageReceived(final WampMessage message) {
        tryScheduleAction(new Runnable() {
            @Override
            public void run() {
                // Drop messages that arrive after close
                if (!forwardIncoming) return;
                
                connectionListener.messageReceived(message);
            }
        });
    }

    @Override
    public void readCompleted(){
        tryScheduleAction(new Runnable() {
            @Override
            public void run() {
                for(IWampConnection connection : flushList){
                    connection.flush();
                }
                flushList.clear();
            }
        });
    }

    @Override
    public void flush(){
        connection.flush();
    }
}
