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

import java.util.ArrayList;
import java.util.List;

public class BasicTokenizer {
    private final boolean doLowerCase;

    public BasicTokenizer(boolean doLowerCase) {
        this.doLowerCase = doLowerCase;
    }

    public List<String> tokenize(String text) {
        text = cleanText(text);
        text = tokenizeCjkChars(text);
        
        List<String> origTokens = whitespaceTokenize(text);
        List<String> splitTokens = new ArrayList<>();
        
        for (String token : origTokens) {
            if (doLowerCase) {
                token = token.toLowerCase();
            }
            splitTokens.addAll(runSplitOnPunc(token));
        }
        
        return whitespaceTokenize(String.join(" ", splitTokens));
    }

    private String cleanText(String text) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0 || c == 0xfffd || CharChecker.isControl(c)) {
                continue;
            }
            if (CharChecker.isWhitespace(c)) {
                output.append(" ");
            } else {
                output.append(c);
            }
        }
        return output.toString();
    }

    private String tokenizeCjkChars(String text) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjkChar(c)) {
                output.append(" ");
                output.append(c);
                output.append(" ");
            } else {
                output.append(c);
            }
        }
        return output.toString();
    }

    private boolean isCjkChar(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) ||
               (c >= 0x3400 && c <= 0x4DBF) ||
               (c >= 0x20000 && c <= 0x2A6DF) ||
               (c >= 0x2A700 && c <= 0x2B73F) ||
               (c >= 0x2B740 && c <= 0x2B81F) ||
               (c >= 0x2B820 && c <= 0x2CEAF) ||
               (c >= 0xF900 && c <= 0xFAFF) ||
               (c >= 0x2F800 && c <= 0x2FA1F);
    }

    private List<String> runSplitOnPunc(String text) {
        List<String> output = new ArrayList<>();
        int startNewWord = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (CharChecker.isPunctuation(c)) {
                if (startNewWord < i) {
                    output.add(text.substring(startNewWord, i));
                }
                output.add(String.valueOf(c));
                startNewWord = i + 1;
            }
        }
        
        if (startNewWord < text.length()) {
            output.add(text.substring(startNewWord));
        }
        
        return output;
    }

    private List<String> whitespaceTokenize(String text) {
        text = text.trim();
        if (text.isEmpty()) {
            return new ArrayList<>();
        }
        String[] tokens = text.split("\\s+");
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }
}