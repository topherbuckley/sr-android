package jp.oist.abcvlib.util;

import jp.oist.abcvlib.util.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;

import static java.net.StandardSocketOptions.SO_SNDBUF;

public class SocketConnectionManager implements Runnable{

    private SocketChannel sc;
    private Selector selector;
    private SocketListener socketListener;
    private final String TAG = "SocketConnectionManager";
    private SocketMessage socketMessage;
    private final InetSocketAddress inetSocketAddress;
    private ByteBuffer episode;
    private CyclicBarrier doneSignal;

    public SocketConnectionManager(SocketListener socketListener,
                                   InetSocketAddress inetSocketAddress,
                                   ByteBuffer episode,
                                   CyclicBarrier doneSignal
                                   ) {
        this.socketListener = socketListener;
        this.inetSocketAddress = inetSocketAddress;
        this.episode = episode;
        this.doneSignal = doneSignal;
    }

    @Override
    public void run() {
        try {
            selector = Selector.open();
            start_connection();
            do {
                int eventCount = selector.select(0);
                Set<SelectionKey> events = selector.selectedKeys(); // events is int representing how many keys have changed state
                if (eventCount != 0){
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    for (SelectionKey selectedKey : selectedKeys){
                        try{
                            SocketMessage socketMessage = (SocketMessage) selectedKey.attachment();
                            socketMessage.process_events(selectedKey);
                            selectedKeys.remove(selectedKey);
                        }catch (ClassCastException e){
                            ErrorHandler.eLog(TAG, "selectedKey attachment not a SocketMessage type", e, true);
                        }
                    }
                }
            } while (selector.isOpen()); //todo remember to close the selector somewhere

            close();

        } catch (IOException e) {
            ErrorHandler.eLog(TAG, "Error", e, true);
        }
    }

    protected void start_connection(){
        try {
            sc = SocketChannel.open();
            sc.configureBlocking(false);
//            sc.setOption(SO_SNDBUF, 2^27);

            Logger.d(TAG, "Initializing connection with " + inetSocketAddress);
            boolean connected = sc.connect(inetSocketAddress);

            socketMessage = new SocketMessage(socketListener, sc, selector);
            Logger.v(TAG, "socketChannel.isConnected ? : " + sc.isConnected());

            socketMessage.addEpisodeToWriteBuffer(episode, doneSignal);

            Logger.v(TAG, "registering with selector to connect");
            int ops = SelectionKey.OP_CONNECT;
            SelectionKey selectionKey = sc.register(selector, ops, socketMessage);
            Logger.v(TAG, "Registered with selector");

        } catch (IOException | ClosedSelectorException | IllegalBlockingModeException
                | CancelledKeyException | IllegalArgumentException e) {
            ErrorHandler.eLog(TAG, "Initial socket connect and registration:", e, true);

        }
    }

    /**
     * Should be called prior to exiting app to ensure zombie threads don't remain in memory.
     */
    public void close(){
        try {
            Logger.v(TAG, "Closing connection: " + sc.getRemoteAddress());
            selector.close();
            sc.close();
        } catch (IOException e) {
            ErrorHandler.eLog(TAG, "Error closing connection", e, true);
        }
    }
}
