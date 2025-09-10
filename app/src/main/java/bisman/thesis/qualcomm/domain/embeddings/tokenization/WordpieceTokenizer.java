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
import java.util.Map;

public class WordpieceTokenizer {
    private final Map<String, Integer> vocab;
    private final String unkToken;
    private final int maxInputCharsPerWord;

    public WordpieceTokenizer(Map<String, Integer> vocab, String unkToken, int maxInputCharsPerWord) {
        this.vocab = vocab;
        this.unkToken = unkToken;
        this.maxInputCharsPerWord = maxInputCharsPerWord;
    }

    public List<String> tokenize(String text) {
        List<String> outputTokens = new ArrayList<>();
        
        for (String token : whitespaceTokenize(text)) {
            if (token.length() > maxInputCharsPerWord) {
                outputTokens.add(unkToken);
                continue;
            }

            boolean isBad = false;
            int start = 0;
            List<String> subTokens = new ArrayList<>();
            
            while (start < token.length()) {
                int end = token.length();
                String curSubstr = null;
                
                while (start < end) {
                    String substr = token.substring(start, end);
                    if (start > 0) {
                        substr = "##" + substr;
                    }
                    if (vocab.containsKey(substr)) {
                        curSubstr = substr;
                        break;
                    }
                    end--;
                }
                
                if (curSubstr == null) {
                    isBad = true;
                    break;
                }
                
                subTokens.add(curSubstr);
                start = end;
            }
            
            if (isBad) {
                outputTokens.add(unkToken);
            } else {
                outputTokens.addAll(subTokens);
            }
        }
        
        return outputTokens;
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