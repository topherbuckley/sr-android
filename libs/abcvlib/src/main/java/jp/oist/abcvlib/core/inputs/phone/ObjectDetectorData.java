package jp.oist.abcvlib.core.inputs.phone;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import jp.oist.abcvlib.util.Logger;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import jp.oist.abcvlib.core.inputs.PublisherManager;

public class ObjectDetectorData extends ImageData<ObjectDetectorDataSubscriber> implements ImageAnalysis.Analyzer {

    private enum delegates{
        DELEGATE_CPU,
        DELEGATE_GPU,
        DELEGATE_NNAPI
    }

    private ObjectDetector objectDetector;
    private final float threshold = 0.5f;
    private final int maxResults = 3;
    private final int numThreads = 2;
    private final int currentDelegate = 0;
    private String currentModel;

    private final String TAG = getClass().getName();

    /*
     * @param previewView: e.g. from your Activity findViewById(R.id.camera_x_preview)
     * @param imageAnalysis: If set to null will generate default imageAnalysis object.
     */
    public ObjectDetectorData(Context context, PublisherManager publisherManager,
                              LifecycleOwner lifecycleOwner, PreviewView previewView,
                              ImageAnalysis imageAnalysis, ExecutorService imageExecutor,
                              delegates currentDelegate, String modelPath){
        super(context, publisherManager, lifecycleOwner, previewView, imageAnalysis, imageExecutor);
        setupObjectDetector(currentDelegate, modelPath);
    }

    // We must specify T to define the extending subclass, S to specify the subscriber type used by the extending subclass, and B to reference the extending subclasses' builder class.
    public static class Builder extends ImageData.Builder<ObjectDetectorData, ObjectDetectorDataSubscriber, ObjectDetectorData.Builder>{
        private delegates delegate = delegates.DELEGATE_CPU; //default CPU
        private String modelPath = "model.tflite"; // default to custom model using pucks/robots only. This is located in the abcvlib/src/main/assets folder

        public Builder(Context context, PublisherManager publisherManager, LifecycleOwner lifecycleOwner) {
            super(context, publisherManager, lifecycleOwner);
            self = self();
        }

        public ObjectDetectorData build(){
            return new ObjectDetectorData(context, publisherManager, lifecycleOwner, previewView, imageAnalysis, imageExecutor, delegate, modelPath);
        }

        @Override
        protected ObjectDetectorData.Builder self() {
            return this;
        }

        public ObjectDetectorData.Builder setDelegate(delegates delegate){
            this.delegate = delegate;
            return this;
        }

        public ObjectDetectorData.Builder setModel(String modelPath){
            this.modelPath = modelPath;
            return this;
        }
    }

    private void setupObjectDetector(delegates currentDelegate, String modelPath){
        ObjectDetector.ObjectDetectorOptions.Builder optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
                .setScoreThreshold(threshold)
                .setMaxResults(maxResults);

        BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads);

        // Use the specified hardware for running the model. Default to CPU
        switch (currentDelegate) {
            case DELEGATE_CPU:
                // Default
                break;
            case DELEGATE_GPU:
                boolean isSupported;
                try (CompatibilityList compatibilityList = new CompatibilityList()) {
                    // Use the compatibilityList instance here
                    isSupported = compatibilityList.isDelegateSupportedOnThisDevice();
                    // Do something with the isSupported boolean value
                }
                if (isSupported) {
                    baseOptionsBuilder.useGpu();
                } else {
                    Logger.e(TAG, "Could not use GPU");
                }
                break;
            case DELEGATE_NNAPI:
                baseOptionsBuilder.useNnapi();
                break;
        }
        optionsBuilder.setBaseOptions(baseOptionsBuilder.build());

// Open the file
        try {
            objectDetector =
                    ObjectDetector.createFromFileAndOptions(context, modelPath, optionsBuilder.build());
        } catch (IOException e) {
            Logger.e(TAG, "Failed to open TFLite model file from assets");
            throw new RuntimeException(e);
        } catch (IllegalStateException e){
            Logger.e(TAG, "TFLite failed to load model with error: " + e.getMessage());
        }
    }


    @Override
    protected void customAnalysis(byte[] imageData, int rotation, int format, int width, int height, long timestamp, Bitmap bitmap) {

        // Inference time is the difference between the system time at the start and finish of the
        // process
        long inferenceTime = SystemClock.uptimeMillis();

        // Create preprocessor for the image.
        // See https://www.tensorflow.org/lite/inference_with_metadata/
        //            lite_support#imageprocessor_architecture
        ImageProcessor imageProcessor = new ImageProcessor.Builder().add(new Rot90Op(-rotation / 90)).build(); //todo check if this rotation is correct

        // Preprocess the image and convert it into a TensorImage for detection.
        TensorImage tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap));

        List<Detection> results = objectDetector.detect(tensorImage);
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime;

        for (ObjectDetectorDataSubscriber subscriber:subscribers){
            subscriber.onObjectsDetected(bitmap, tensorImage, results, inferenceTime, height, width);
        }
    }

}
