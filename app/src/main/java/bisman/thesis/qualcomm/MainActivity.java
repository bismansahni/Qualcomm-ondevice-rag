// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
package bisman.thesis.qualcomm;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    static {
        System.loadLibrary("chatapp");
    }

    /**
     * copyAssetsDir: Copies provided assets to output path
     *
     * @param inputAssetRelPath relative path to asset from asset root
     * @param outputPath        output path to copy assets to
     * @throws IOException
     * @throws NullPointerException
     */
    void copyAssetsDir(String inputAssetRelPath, String outputPath) throws IOException, NullPointerException {
        File outputAssetPath = new File(Paths.get(outputPath, inputAssetRelPath).toString());

        String[] subAssetList = this.getAssets().list(inputAssetRelPath);
        if (subAssetList.length == 0) {
            // If file already present, skip copy.
            if (!outputAssetPath.exists()) {
                copyFile(inputAssetRelPath, outputAssetPath);
            }
            return;
        }

        // Input asset is a directory, create directory if not present already.
        if (!outputAssetPath.exists()) {
            outputAssetPath.mkdirs();
        }
        for (String subAssetName : subAssetList) {
            // Copy content of sub-directory
            String input_sub_asset_path = Paths.get(inputAssetRelPath, subAssetName).toString();
            // NOTE: Not to modify output path, relative asset path is being updated.
            copyAssetsDir(input_sub_asset_path, outputPath);
        }
    }

    /**
     * copyFile: Copies provided input file asset into output asset file
     *
     * @param inputFilePath   relative file path from asset root directory
     * @param outputAssetFile output file to copy input asset file into
     * @throws IOException
     */
    void copyFile(String inputFilePath, File outputAssetFile) throws IOException {
        InputStream in = this.getAssets().open(inputFilePath);
        OutputStream out = new FileOutputStream(outputAssetFile);

        byte[] buffer = new byte[1024 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // HTP config is guaranteed to be 8gen3
            String htpConfigFile = "qualcomm-snapdragon-8-gen3.json";

            // Copy assets to External cache
            //  - <assets>/models
            //      - has list of models with tokenizer.json, genie_config.json and model binaries
            //  - <assets>/htp_config/
            //      - has qualcomm-snapdragon-8-gen3.json config file
            String externalDir = getExternalCacheDir().getAbsolutePath();
            try {
                // Copy assets to External cache if not already present
                copyAssetsDir("models", externalDir.toString());
                copyAssetsDir("htp_config", externalDir.toString());
            } catch (IOException e) {
                String errorMsg = "Error during copying model asset to external storage: " + e.toString();
                Log.e("ChatApp", errorMsg);
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                finish();
            }
            Path htpExtConfigPath = Paths.get(externalDir, "htp_config", htpConfigFile);
            
            // Set the paths that will be used by MainComposeActivity
            MainComposeActivity.modelDirectory = Paths.get(externalDir, "models", "llm").toString();
            MainComposeActivity.htpConfigPath = htpExtConfigPath.toString();

            // Skip the button screen and go directly to Compose UI
            Intent intent = new Intent(MainActivity.this, MainComposeActivity.class);
            startActivity(intent);
            finish(); // Close this activity so user can't go back to it
        } catch (Exception e) {
            String errorMsg = "Unexpected error occurred while running ChatApp:" + e.toString();
            Log.e("ChatApp", errorMsg);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
