package jp.oist.abcvlib.util;

import jp.oist.abcvlib.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Vector;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class SocketMessage {
    
    private final SocketChannel sc;
    private final Selector selector;
    private final ByteBuffer _recv_buffer;
    private ByteBuffer _send_buffer;
    private int _jsonheader_len = 0;
    private JSONObject jsonHeaderRead; // Will tell Java at which points in msgContent each model lies (e.g. model1 is from 0 to 1018, model2 is from 1019 to 2034, etc.)
    private byte[] jsonHeaderBytes;
    private ByteBuffer msgContent; // Should contain ALL model files. Parse to individual files after reading
    private final Vector<ByteBuffer> writeBufferVector = new Vector<>(); // List of episodes
    private final String TAG = "SocketConnectionManager";
    private JSONObject jsonHeaderWrite;
    private boolean msgReadComplete = false;
    private SocketListener socketListener;
    private long socketWriteTimeStart;
    private long socketReadTimeStart;
    private int totalNumBytesToWrite;
    private CyclicBarrier doneSignal; // used to notify main thread that write/read to server has finished


    public SocketMessage(SocketListener socketListener, SocketChannel sc, Selector selector){
        this.socketListener = socketListener;
        this.sc = sc;
        this.selector = selector;
//        this._recv_buffer = ByteBuffer.allocate((int) Math.pow(2,24));
        this._recv_buffer = ByteBuffer.allocate(1024);
        this._send_buffer = ByteBuffer.allocate(1024);
    }

    public void process_events(SelectionKey selectionKey){
        SocketChannel sc = (SocketChannel) selectionKey.channel();
//        Logger.i(TAG, "process_events");
        try{
            if (selectionKey.isConnectable()){
                boolean connected = sc.finishConnect();
                if (connected){
                    Logger.d(TAG, "Finished connecting to " + ((SocketChannel) selectionKey.channel()).getRemoteAddress());
                    Logger.v(TAG, "socketChannel.isConnected ? : " + sc.isConnected());
                    int ops = SelectionKey.OP_WRITE;
                    sc.register(selectionKey.selector(), ops, selectionKey.attachment());
                }
            }
            if (selectionKey.isWritable()){
//                Logger.i(TAG, "write event");
                write(selectionKey);
            }
            if (selectionKey.isReadable()){
//                Logger.i(TAG, "read event");
                read(selectionKey);

//                int ops = SelectionKey.OP_WRITE;
//                sc.register(selectionKey.selector(), ops, selectionKey.attachment());
            }

        } catch (ClassCastException | IOException | JSONException | BrokenBarrierException | InterruptedException e){
            ErrorHandler.eLog(TAG, "Error processing selector events", e, true);
        }
    }

    private void read(SelectionKey selectionKey) throws IOException, JSONException, BrokenBarrierException, InterruptedException {

        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

        while(!msgReadComplete){
            // At this point the _recv_buffer should have been cleared (pointer 0 limit=cap, no mark)
            int bitsRead = socketChannel.read(_recv_buffer);

            if (bitsRead > 0 || _recv_buffer.position() > 0){
                // If you have not determined the length of the header via the 2 byte short protoheader,
                // try to determine it, though there is no gaurantee it will have enough bytes. So it may
                // pass through this if statement multiple times. Only after it has been read will
                // _jsonheader_len have a non-zero length;
                if (this._jsonheader_len == 0){
                    socketReadTimeStart = System.nanoTime();
                    process_protoheader();
                }
                // _jsonheader_len will only be larger than 0 if set properly (finished being set).
                // jsonHeaderRead will be null until the buffer gathering it has filled and converted it to
                // a JSONobject.
                else if (this.jsonHeaderRead == null){
                    process_jsonheader();
                }
                else if (!msgReadComplete){
                    process_msgContent(selectionKey);
                } else {
                    Logger.e(TAG, "bitsRead but don't know what to do with them");
                }
            }
            // If msgContent is zero this handles it.
            else if (msgContent != null && !msgReadComplete){
                process_msgContent(selectionKey);
            }
        }
    }

    private void write(SelectionKey selectionKey) throws IOException, JSONException, BrokenBarrierException, InterruptedException {

        if (!writeBufferVector.isEmpty()){
            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

//            Logger.v(TAG, "writeBufferVector contains data");

            if (jsonHeaderWrite == null){
                // This is because the data in this ByteBuffer does NOT start at 0, but at
                // buf.position(). Even if it were some other type of buffer here (e.g. compacted one)
                // the position should be zero and thus this shouldn't change anything
                int numBytesToWrite = writeBufferVector.get(0).limit() - writeBufferVector.get(0).position();

                // Create JSONHeader containing length of episode in Bytes
                Logger.v(TAG, "generating jsonheader");
                jsonHeaderWrite = generate_jsonheader(numBytesToWrite);
                byte[] jsonBytes = jsonHeaderWrite.toString().getBytes(StandardCharsets.UTF_8);
//                ByteBuffer jsonByteBuffer = ByteBuffer.wrap(jsonBytes); //todo optimize buffer length

                // Encode length of JSONHeader to first four bytes (int) and write to socketChannel
                int jsonLength = jsonBytes.length;

                // Add up length of protoHeader, JSONheader and episode bytes
                totalNumBytesToWrite = Integer.BYTES + jsonLength + numBytesToWrite;

//                int optimalBufferSize = findOptimalBufferSize(totalNumBytesToWrite);

                // Create new buffer that compiles protoHeader, JsonHeader, and Episode
                _send_buffer = ByteBuffer.allocate(Integer.BYTES + jsonLength);

                Logger.v(TAG, "Assembling _send_buffer");
                // Assemble all bytes and flip to prepare to read
                // todo try to write the episode directly rather than copy it.
                _send_buffer.putInt(jsonLength);
                _send_buffer.put(jsonBytes);
                // Remove episode to clear memory note builder will reference the flatbuffer builder in memory
//                ByteBuffer builder = writeBufferVector.remove(0);
//                builder = null;

                _send_buffer.flip();

                int total = _send_buffer.limit();

                Logger.d(TAG, "Writing JSONHeader of length " + total + " bytes to server ...");

                // Write Bytes to socketChannel
                if (_send_buffer.remaining() > 0){
                    int numBytes = socketChannel.write(_send_buffer); // todo memory dump error here!
                }

                int msgSize = writeBufferVector.get(0).limit() / 1000000;
                Logger.d(TAG, "Writing message of length " + msgSize + "MB to server ...");

            } else{
                // Write Bytes to socketChannel
                if (_send_buffer.remaining() > 0){
                    socketChannel.write(_send_buffer);
                }else if (writeBufferVector.get(0).remaining() > 0){
                    int bytes = socketChannel.write(writeBufferVector.get(0));
                    printTotalBytes(socketChannel, bytes);
                }
            }
            if (writeBufferVector.get(0).remaining() == 0){
                int total = writeBufferVector.get(0).limit() / 1000;
                double timeTaken = (System.nanoTime() - socketWriteTimeStart) * 10e-10;
                DecimalFormat df = new DecimalFormat();
                df.setMaximumFractionDigits(2);
                Logger.i(TAG, "Sent " + total + "kb in " + df.format(timeTaken) + "s");
                Logger.i(TAG, "Mean transfer rate of " + df.format(total/timeTaken) + " MB/s");

                // Clear sending buffer
                _send_buffer.clear();
                writeBufferVector.get(0).clear();
                writeBufferVector.remove(0);
                // make null so as to catch the initial if statement to write a new one.
                jsonHeaderWrite = null;

                // Set socket to read now that writing has finished.
                Logger.d(TAG, "Reading from server ...");
                int ops = SelectionKey.OP_READ; //todo might need to reconnect if send buffer empties
                sc.register(selectionKey.selector(), ops, selectionKey.attachment());
            }

        }
    }

    private void printTotalBytes(SocketChannel socketChannel, int bytesWritten) throws IOException {
        int percentDone = (int) Math.ceil(((double) totalNumBytesToWrite - (double) writeBufferVector.get(0).remaining())
                / (double) totalNumBytesToWrite * 100);
        int total = totalNumBytesToWrite / 1000000;
        Logger.d(TAG, "Sent " + percentDone + "% of " + total + "Mb to " + socketChannel.getRemoteAddress());
    }

    private int findOptimalBufferSize(int dataSize){
        int optimalBufferSize;

        int closestLog2 = (int) Math.ceil(Math.log(dataSize) / Math.log(2));

        optimalBufferSize = (int) Math.pow(closestLog2, 2);

        return optimalBufferSize;
    }

    private JSONObject generate_jsonheader(int numBytesToWrite) throws JSONException {
        JSONObject jsonHeader = new JSONObject();

        jsonHeader.put("byteorder", ByteOrder.nativeOrder().toString());
        jsonHeader.put("content-length", numBytesToWrite);
        jsonHeader.put("content-type", "episode");
        jsonHeader.put("content-encoding", "flatbuffer");
        return jsonHeader;
    }

    /**
     * recv_buffer may contain 0, 1, or several bytes. If it has more than hdrlen, then process
     * the first two bytes to obtain the length of the jsonheader. Else exit this function and
     * read from the buffer again until it fills past length hdrlen.
     */
    private void process_protoheader() {
        Logger.v(TAG, "processing protoheader");
        int hdrlen = 2;
        if (_recv_buffer.position() >= hdrlen){
            _recv_buffer.flip(); //pos at 0 and limit set to bitsRead
            _jsonheader_len = _recv_buffer.getShort(); // Read 2 bytes converts to short and move pos to 2
            // allocate new ByteBuffer to store full jsonheader
            jsonHeaderBytes = new byte[_jsonheader_len];

            _recv_buffer.compact();

            Logger.v(TAG, "finished processing protoheader");
        }
    }

    /**
     *  As with the process_protoheader we will check if _recv_buffer contains enough bytes to
     *  generate the jsonHeader objects, and if not, leave it alone and read more from socket.
     */
    private void process_jsonheader() throws JSONException {

        Logger.v(TAG, "processing jsonheader");

        // If you have enough bytes in the _recv_buffer to write out the jsonHeader
        if (_jsonheader_len - _recv_buffer.position() <= 0){
            _recv_buffer.flip();
            _recv_buffer.get(jsonHeaderBytes);
            // jsonheaderBuffer should now be full and ready to convert to a JSONobject
            jsonHeaderRead = new JSONObject(new String(jsonHeaderBytes));
            Logger.d(TAG, "JSONheader from server: " + jsonHeaderRead.toString());

            try{
                int msgLength = (int) jsonHeaderRead.get("content-length");
                msgContent = ByteBuffer.allocate(msgLength);
            }catch (JSONException e) {
                ErrorHandler.eLog(TAG, "Couldn't get content-length from jsonHeader sent from server", e, true);
            }
        }
        // Else return to selector and read more bytes into the _recv_buffer

        // If there are any bytes left over (part of the msg) then move them to the front of the buffer
        // to prepare for another read from the socket
        _recv_buffer.compact();
    }

    /**
     * Here a bit different as it may take multiple full _recv_buffers to fill the msgContent.
     * So check if msgContent.remaining is larger than 0 and if so, dump everything from _recv_buffer to it
     * @param selectionKey : Used to reference the instance and selector
     * @throws ClosedChannelException :
     */
    private void process_msgContent(SelectionKey selectionKey) throws IOException, BrokenBarrierException, InterruptedException {

        if (msgContent.remaining() > 0){
            _recv_buffer.flip(); //pos at 0 and limit set to bitsRead set ready to read
            msgContent.put(_recv_buffer);
            _recv_buffer.clear();
        }

        if (msgContent.remaining() == 0){
            // msgContent should now be full and ready to convert to a various model files.
            socketListener.onServerReadSuccess(jsonHeaderRead, msgContent);

            // Clear for next round of communication
            _recv_buffer.clear();
            _jsonheader_len = 0;
            jsonHeaderRead = null;
            msgContent.clear();

            int totalBytes = msgContent.capacity() / 1000000;
            msgContent = null;
            double timeTaken = (System.nanoTime() - socketReadTimeStart) * 10e-10;
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(2);
            Logger.i(TAG, "Entire message containing " + totalBytes + "Mb recv'd in " + df.format(timeTaken) + "s");

            msgReadComplete = true;

//            // Set socket to write now that reading has finished.
//            int ops = 0;
//            sc.register(selectionKey.selector(), ops, selectionKey.attachment());
            selectionKey.cancel();
            selector.close();

            doneSignal.await();
        }
    }

    //todo should send this to the mainactivity listener so it can be customized/overridden
    private void onNewMessageFromServer(){
        // Take info from JSONheader to parse msgContent into individual model files

        // After parsing all models notify MainActivity that models have been updated
    }

    // todo should be able deal with ByteBuffer from FlatBuffer rather than byte[]
    public void addEpisodeToWriteBuffer(ByteBuffer episode, CyclicBarrier doneSignal){
        boolean success = false;
        try{
            success = writeBufferVector.add(episode); // does pos or limit change in either episode or writeBufferVector at this point?
            this.doneSignal = doneSignal;
            Logger.v(TAG, "Added data to writeBuffer");
            int ops = SelectionKey.OP_WRITE;
            socketWriteTimeStart = System.nanoTime();
            sc.register(selector, ops, this);
//            socketConnectionManager.start_connection();
            // I want this to trigger the selector that this channel is writeReady.
        } catch (NullPointerException | ClosedChannelException e){
            ErrorHandler.eLog(TAG, "SocketConnectionManager.data not initialized yet", e, true);
        }
    }
}
