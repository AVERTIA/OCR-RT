package com.avertia.rt.ocr;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.support.annotation.NonNull;
import android.text.format.DateFormat;

import com.avertia.rt.ocr.ui.camera.GraphicOverlay;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextDetector;

import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.List;

public class DetectorFirebase {

    private GraphicOverlay graphicOverlay;
    FirebaseVisionTextDetector detector;
    private boolean alb;
    private boolean ped;

    DetectorFirebase(GraphicOverlay ocrGraphicOverlay) {
        detector = FirebaseVision.getInstance().getVisionTextDetector();
        graphicOverlay = ocrGraphicOverlay;
        restartVariables();
    }

    private void restartVariables() {
        alb = false;
        ped = false;
    }

    public void release() {
        graphicOverlay.clear();
    }

    public TextBlock widenBlock(TextBlock block, float offset) {
        block.getBoundingBox().left -= offset;
        block.getBoundingBox().right += offset;
        block.getBoundingBox().top -= offset;
        block.getBoundingBox().bottom += offset;
        return block;
    }

    private void printTime(String literal) {
        Date d = new Date();
        CharSequence s = DateFormat.format("hh:mm:ss", d.getTime());
        System.out.println(literal + s.toString());
    }

    private Bitmap getBitmap(Frame frame){
        int w = frame.getMetadata().getWidth();
        int h = frame.getMetadata().getHeight();

        YuvImage yuvimage=new YuvImage(frame.getGrayscaleImageData().array(), ImageFormat.NV21,w,h,null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        yuvimage.compressToJpeg(new Rect(0, 0, w, h), 100, baos); // Where 100 is the quality of the generated jpeg
        byte[] jpegArray = baos.toByteArray();
        BitmapFactory.Options options = new BitmapFactory.Options();
        //options.inSampleSize = 2;

        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegArray, 0, jpegArray.length,  options);
        return bitmap;
    }

    private FirebaseVisionImageMetadata getMetadata(Frame frame){
        return new FirebaseVisionImageMetadata.Builder()
                .setFormat(frame.getMetadata().getFormat())
                .setHeight(frame.getMetadata().getHeight())
                .setRotation(frame.getMetadata().getRotation())
                .setWidth(frame.getMetadata().getWidth())
                .build();
    }

    public void detect(Frame outputFrame) {
        final FirebaseVisionImage image = FirebaseVisionImage.fromByteBuffer(outputFrame.getGrayscaleImageData(), getMetadata(outputFrame));
        detector.detectInImage(image).addOnSuccessListener(
            new OnSuccessListener<FirebaseVisionText>() {
                @Override
                public void onSuccess(FirebaseVisionText texts) {
                    processTextRecognitionResult(texts);
                }
            })
            .addOnFailureListener(
                    new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            // Task failed with an exception
                            e.printStackTrace();
                            image.getBitmapForDebugging().recycle();
                        }
                    });

    }

    private void processTextRecognitionResult(FirebaseVisionText texts) {
        // TODO: Add your code here to process the on-device text recognition results.
        if(graphicOverlay.isEmpty()) restartVariables();
        List<FirebaseVisionText.Block> blocks = texts.getBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            List<FirebaseVisionText.Line> lines = blocks.get(i).getLines();
            for (int j = 0; j < lines.size(); j++) {
                List<FirebaseVisionText.Element> elements = lines.get(j).getElements();
                for (int k = 0; k < elements.size(); k++) {
                    FirebaseVisionText.Element item = elements.get(k);
                    String itemText = item.getText();
                    if (!alb && itemText.matches("^404[0-9]{4}")){
                        alb = true;
                        GraphicOverlay.Graphic textGraphic = new TextGraphic(graphicOverlay, elements.get(k));
                        graphicOverlay.add(textGraphic);
                    }
                    if(!ped && itemText.matches("^45[0-9]{8}(/[0-9]+)?$")) {
                        ped = true;
                        GraphicOverlay.Graphic textGraphic = new TextGraphic(graphicOverlay, elements.get(k));
                        graphicOverlay.add(textGraphic);
                    }
                }
            }
        }
    }
}
