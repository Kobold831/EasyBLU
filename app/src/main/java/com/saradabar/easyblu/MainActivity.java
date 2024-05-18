package com.saradabar.easyblu;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity {

    DataOutputStream dos;
    BufferedReader bufferedReader, bufferedReader1;
    String str, str2;
    StringBuilder stringBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        new Handler().postDelayed(this::run, 1000);
    }

    void run() {
        stringBuilder = new StringBuilder();
        str2 = System.lineSeparator();

        copyAssetsFile(this);

        init();
        execute("chmod 770 " + new File(getFilesDir(), "shrinker").getAbsolutePath());
        execute("/data/data/com.saradabar.easyblu/files/shrinker");
        String text = getText().toString();

        if (text.contains("result 49")) {
            Log.e("TAG", text);
            Log.e("TAG", "成功");
            TextView textView = findViewById(R.id.text);
            textView.setText("成功しました");
        } else {
            Log.e("TAG", text);
            Log.e("TAG", "失敗");
            b();
        }
    }

    void b() {
        execute("/data/data/com.saradabar.easyblu/files/shrinker");
        String text = getText().toString();

        if (text.contains("result 49")) {
            Log.e("TAG", text);
            Log.e("TAG", "成功");
            TextView textView = findViewById(R.id.text);
            textView.setText("成功しました");
        } else {
            Log.e("TAG", text);
            Log.e("TAG", "失敗");
            b();
        }
    }

    public static void copyAssetsFile(Context context) {
        try {
            InputStream inputStream = context.getAssets().open("shrinker");
            FileOutputStream fileOutputStream = new FileOutputStream(new File(context.getFilesDir(), "shrinker"), false);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) >= 0) {
                fileOutputStream.write(buffer, 0, length);
            }
            fileOutputStream.close();
            inputStream.close();
        } catch (IOException ignored) {
        }
    }

    public void init() {
        try {
            Process process = Runtime.getRuntime().exec("sh");
            dos = new DataOutputStream(process.getOutputStream());
            bufferedReader = new BufferedReader(new InputStreamReader(new DataInputStream(process.getInputStream())));
            bufferedReader1 = new BufferedReader(new InputStreamReader(new DataInputStream(process.getErrorStream())));
        } catch (Exception ignored) {
        }
    }

    public StringBuilder getText() {
        try {
            int i = 0;
            stringBuilder.setLength(0);
            while (i <= 500) {
                if (bufferedReader.ready()) {
                    str = bufferedReader.readLine();
                    stringBuilder.append(str);
                    stringBuilder.append(str2);

                    if (str != null) {
                        i = 0;
                    } else {
                        break;
                    }
                } else {
                    try {
                        Thread.sleep(10);
                    } catch (Exception ignored) {
                    }

                    i++;
                }
            }
        } catch (Exception ignored) {
        }

        return stringBuilder;
    }

    public void execute(String str) {
        try {
            dos.writeBytes(str + "\n");
            dos.flush();
        } catch (Exception ignored) {
        }
    }
}
