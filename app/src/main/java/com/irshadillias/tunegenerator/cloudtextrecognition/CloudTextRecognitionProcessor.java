// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.irshadillias.tunegenerator.cloudtextrecognition;

import android.graphics.Bitmap;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.irshadillias.tunegenerator.common.FrameMetadata;
import com.irshadillias.tunegenerator.common.GraphicOverlay;
import com.irshadillias.tunegenerator.common.VisionProcessorBase;

import java.util.List;

/**
 * Processor for the cloud text detector demo.
 */
public class CloudTextRecognitionProcessor extends VisionProcessorBase<FirebaseVisionText> {

    private static final String TAG = "CloudTextRecProc";

    private final FirebaseVisionTextRecognizer detector;

    public CloudTextRecognitionProcessor() {
        super();
        detector = FirebaseVision.getInstance().getCloudTextRecognizer();
    }

    @Override
    protected Task<FirebaseVisionText> detectInImage(FirebaseVisionImage image) {
        return detector.processImage(image);
    }

    @Override
    protected void onSuccess(
            @Nullable Bitmap originalCameraImage,
            @NonNull FirebaseVisionText text,
            @NonNull FrameMetadata frameMetadata,
            @NonNull GraphicOverlay graphicOverlay) {
        graphicOverlay.clear();
        if (text == null) {
            return; // TODO: investigate why this is needed
        }
        List<FirebaseVisionText.TextBlock> blocks = text.getTextBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            List<FirebaseVisionText.Line> lines = blocks.get(i).getLines();
            for (int j = 0; j < lines.size(); j++) {
                List<FirebaseVisionText.Element> elements = lines.get(j).getElements();
                for (int l = 0; l < elements.size(); l++) {
                    CloudTextGraphic cloudTextGraphic = new CloudTextGraphic(graphicOverlay,
                            elements.get(l));
                    graphicOverlay.add(cloudTextGraphic);
                }
            }
        }
        graphicOverlay.postInvalidate();
    }

    @Override
    protected void onSuccess(@Nullable Bitmap originalCameraImage, @NonNull FirebaseVisionText results, @NonNull FrameMetadata frameMetadata, @NonNull TextView graphicOverlay) {
        graphicOverlay.setText("");
        String resultvalue = "";
        if (results == null) {
            return; // TODO: investigate why this is needed
        }
        List<FirebaseVisionText.TextBlock> blocks = results.getTextBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            List<FirebaseVisionText.Line> lines = blocks.get(i).getLines();
            for (int j = 0; j < lines.size(); j++) {
                List<FirebaseVisionText.Element> elements = lines.get(j).getElements();
                for (int l = 0; l < elements.size(); l++) {
                    resultvalue += " "+elements.get(l).getText();
                }
            }
        }
        graphicOverlay.setText(resultvalue);
        //graphicOverlay.postInvalidate();
    }

    @Override
    protected void onFailure(@NonNull Exception e) {
        Log.w(TAG, "Cloud Text detection failed." + e);
    }
}
