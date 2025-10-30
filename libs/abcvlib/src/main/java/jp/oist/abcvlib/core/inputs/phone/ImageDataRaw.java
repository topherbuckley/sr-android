package jp.oist.abcvlib.core.inputs.phone;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import jp.oist.abcvlib.util.Logger;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jp.oist.abcvlib.core.inputs.PublisherManager;
import jp.oist.abcvlib.util.ProcessPriorityThreadFactory;

public class ImageDataRaw extends ImageData<ImageDataRawSubscriber> implements ImageAnalysis.Analyzer {

    /*
     * @param previewView: e.g. from your Activity findViewById(R.id.camera_x_preview)
     * @param imageAnalysis: If set to null will generate default imageAnalysis object.
     */
    public ImageDataRaw(Context context, PublisherManager publisherManager,
                        LifecycleOwner lifecycleOwner, PreviewView previewView,
                        ImageAnalysis imageAnalysis, ExecutorService imageExecutor){
        super(context, publisherManager, lifecycleOwner, previewView, imageAnalysis, imageExecutor);
    }

    // We must specify T to define the extending subclass, S to specify the subscriber type used by the extending subclass, and B to reference the extending subclasses' builder class.
    public static class Builder extends ImageData.Builder<ImageDataRaw, ImageDataRawSubscriber, ImageDataRaw.Builder>{
        public Builder(Context context, PublisherManager publisherManager, LifecycleOwner lifecycleOwner) {
            super(context, publisherManager, lifecycleOwner);
            self = self();
        }

        public ImageDataRaw build(){
            return new ImageDataRaw(context, publisherManager, lifecycleOwner, previewView, imageAnalysis, imageExecutor);
        }

        @Override
        protected ImageDataRaw.Builder self() {
            return this;
        }
    }

    public ImageAnalysis getImageAnalysis() {
        return imageAnalysis;
    }

    @Override
    protected void customAnalysis(byte[] imageData, int rotation, int format, int width, int height, long timestamp, Bitmap bitmap) {
        for (ImageDataRawSubscriber subscriber:subscribers){
            subscriber.onImageDataRawUpdate(timestamp, width, height, bitmap);
        }
    }
}
