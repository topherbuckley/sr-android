package jp.oist.abcvlib.basicserver;

import android.app.Activity;
import android.os.Bundle;
import jp.oist.abcvlib.util.Logger;

import com.google.flatbuffers.FlatBufferBuilder;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import jp.oist.abcvlib.util.HttpConnection;
import jp.oist.abcvlib.core.learning.fbclasses.Episode;
import jp.oist.abcvlib.util.HttpDataType;
import jp.oist.abcvlib.util.HttpExtraInfo;
import jp.oist.abcvlib.util.ProcessPriorityThreadFactory;
import jp.oist.abcvlib.util.ScheduledExecutorServiceWithException;

public class MainActivity extends Activity implements HttpConnection.HttpCallback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ScheduledExecutorServiceWithException scheduledExecutorServiceWithException =
                new ScheduledExecutorServiceWithException(1, new ProcessPriorityThreadFactory(Thread.NORM_PRIORITY, "HttpConnection"));
        scheduledExecutorServiceWithException.scheduleWithFixedDelay(this::loopingHttpCalls, 0, 5, TimeUnit.SECONDS);
    }

    private void loopingHttpCalls(){
        HttpConnection httpConnection = new HttpConnection(this, this);

        // Send a string
        byte[] stringData = "Hello, this is a string!".getBytes();
        httpConnection.sendData(stringData, HttpDataType.STRING, null);

        // Send a file
        byte[] fileData = {/* File data as byte array */};
        HttpExtraInfo.FileInfo fileInfo = new HttpExtraInfo.FileInfo("filename.txt", 1234, "text/plain");
        httpConnection.sendData(fileData, HttpDataType.FILE, fileInfo);

        // Send a flatbuffer
        int robotID = 1;
        int flatbufferSize = 1024;
        FlatBufferBuilder builder = new FlatBufferBuilder(flatbufferSize);
        Episode.startEpisode(builder);
        Episode.addRobotid(builder, robotID);
        int ep = Episode.endEpisode(builder);
        builder.finish(ep);
        ByteBuffer episode = builder.dataBuffer();
        byte[] flatbufferData = episode.array();
        int size = flatbufferData.length;
        HttpExtraInfo.FlatbufferInfo flatbufferInfo = new HttpExtraInfo.FlatbufferInfo("FlatbufferClassName", size);
        httpConnection.sendData(flatbufferData, HttpDataType.FLATBUFFER, flatbufferInfo);

        // Request a file
        httpConnection.getData("test.txt");
    }

    @Override
    public void onFileReceived(String filename, byte[] fileData) {
        // Handle the received file data
        Logger.d("HttpConnection", "Received file: " + filename + " of length: " + fileData.length);
    }

    @Override
    public void onSuccess(String response) {
        // Handle the received string response
        Logger.d("HttpConnection", "Received response: " + response);
    }

    @Override
    public void onError(String error) {
        // Handle the error
        Logger.e("HttpConnection", "Error: " + error);
    }
}