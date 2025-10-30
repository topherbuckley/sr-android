package jp.oist.abcvlib.core.inputs.phone;

import static android.graphics.ImageFormat.YUV_420_888;
import static android.graphics.ImageFormat.YUV_422_888;
import static android.graphics.ImageFormat.YUV_444_888;

import android.content.Context;
import android.graphics.Bitmap;
import jp.oist.abcvlib.util.Logger;
import android.util.Size;

import androidx.camera.core.ImageAnalysis;
import androidx.lifecycle.LifecycleOwner;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jp.oist.abcvlib.core.inputs.PublisherManager;
import jp.oist.abcvlib.util.ProcessPriorityThreadFactory;

public class QRCodeData extends ImageData<QRCodeDataSubscriber>{

    public QRCodeData(Context context, PublisherManager publisherManager, LifecycleOwner lifecycleOwner,
                      ImageAnalysis imageAnalysis, ExecutorService imageExecutor) {
        super(context, publisherManager, lifecycleOwner, null, imageAnalysis, imageExecutor);
    }

    public static class Builder extends ImageData.Builder<QRCodeData, QRCodeDataSubscriber, Builder>{

        public Builder(Context context, PublisherManager publisherManager, LifecycleOwner lifecycleOwner) {
            super(context, publisherManager, lifecycleOwner);
            self = self();
        }

        public QRCodeData build(){
            return new QRCodeData(context, publisherManager, lifecycleOwner, imageAnalysis, imageExecutor);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }

    @Override
    protected void customAnalysis(byte[] imageData, int rotation, int format, int width, int height, long timestamp, Bitmap bitmap){
        String qrDecodedData = "";
        if (format == YUV_420_888 || format == YUV_422_888 || format == YUV_444_888) {
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    imageData,
                    width, height,
                    0, 0,
                    width, height,
                    false
            );

            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

            try {
                Result result = new QRCodeReader().decode(binaryBitmap);
                qrDecodedData = result.getText();
                for (QRCodeDataSubscriber subscriber:subscribers){
                    subscriber.onQRCodeDetected(qrDecodedData);
                }
            } catch (FormatException e) {
                Logger.v("qrcode", "QR Code cannot be decoded");
            } catch (ChecksumException e) {
                Logger.v("qrcode", "QR Code error correction failed");
                e.printStackTrace();
            } catch (NotFoundException e) {
                Logger.v("qrcode", "QR Code not found");
            }
        }
    }

    @Override
    protected void setDefaultImageAnalysis(){
        imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(400, 300))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setImageQueueDepth(4)
                        .build();
    }
}
