/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package bisman.thesis.qualcomm.domain.embeddings.tokenization;

public class CharChecker {
    
    public static boolean isWhitespace(char c) {
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
            return true;
        }
        int cat = Character.getType(c);
        return cat == Character.SPACE_SEPARATOR ||
               cat == Character.LINE_SEPARATOR ||
               cat == Character.PARAGRAPH_SEPARATOR;
    }

    public static boolean isControl(char c) {
        if (c == '\t' || c == '\n' || c == '\r') {
            return false;
        }
        int cat = Character.getType(c);
        return cat == Character.CONTROL || cat == Character.FORMAT;
    }

    public static boolean isPunctuation(char c) {
        int cat = Character.getType(c);
        return cat == Character.CONNECTOR_PUNCTUATION ||
               cat == Character.DASH_PUNCTUATION ||
               cat == Character.END_PUNCTUATION ||
               cat == Character.FINAL_QUOTE_PUNCTUATION ||
               cat == Character.INITIAL_QUOTE_PUNCTUATION ||
               cat == Character.OTHER_PUNCTUATION ||
               cat == Character.START_PUNCTUATION;
    }
}