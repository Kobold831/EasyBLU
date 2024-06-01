package com.saradabar.easyblu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import jp.co.benesse.dcha.dchaservice.IDchaService;

public class MainActivity extends Activity {

    DataOutputStream dos;
    BufferedReader bufferedReader, bufferedReader1;
    StringBuilder stringBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        addText("****************************");
        addText("Welcome to Easy BLU ! :)");
        addText("Easy BLU へようこそ！");
        addText("****************************");
        addText("fingerprint：" + Build.FINGERPRINT);
        init();
    }

    void init() {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("実行しますか？")
                .setMessage("続行するには OK を押下してください\n\nキャンセルを押すと Android 設定に遷移します")
                .setPositiveButton("OK", (dialog, which) -> {
                    addText("- 通知：shrinker を実行しました");
                    addText("- 警告：デバイスには絶対に触れないでください。処理が終了するまでお待ち下さい。");
                    addText("- 警告：デバイスが再起動した場合は失敗です。起動後に再度実行してください。");
                    new Handler().postDelayed(() -> {
                        String result = shrinker();
                        if (result.contains("result 49")) {
                            addText("- 通知：成功しました。");
                            addText("- 通知：frp.bin の修正を試みます。");
                            new Handler().postDelayed(this::overwriteFrp, 5000);
                        } else {
                            addText("- 通知：失敗しました。再度実行します。");
                            new Handler().postDelayed(this::retry, 5000);
                        }
                    }, 5000);
                })
                .setNegativeButton("キャンセル", (dialog, which) -> {
                    try {
                        Settings.System.putInt(getContentResolver(), "dcha_state", 3);
                    } catch (Exception ignored) {
                    }
                    startActivity(new Intent().setClassName("com.android.settings", "com.android.settings.Settings"));
                })
                .show();
    }

    @SuppressLint("SdCardPath")
    String shrinker() {
        stringBuilder = new StringBuilder();
        addText("- 通知：" + getFilesDir() + " にファイルをコピーしています。");
        copyAssetsFile(this);
        sh();
        addText("- 通知：実行権限を付与しています。");
        execute("chmod 770 " + new File(getFilesDir(), "shrinker").getAbsolutePath());
        execute("/data/data/com.saradabar.easyblu/files/shrinker");
        String text = getText().toString();
        addText("- 結果：");
        addText(text);
        return text;
    }

    @SuppressLint("SdCardPath")
    void retry() {
        execute("/data/data/com.saradabar.easyblu/files/shrinker");
        String text = getText().toString();
        addText("- 結果:");
        addText(text);
        if (text.contains("result 49")) {
            addText("- 通知：成功しました。");
            addText("- 通知：frp.bin の修正を試みます。");
            new Handler().postDelayed(this::overwriteFrp, 5000);
        } else {
            addText("- 通知：失敗しました。再度実行します。");
            new Handler().postDelayed(this::retry, 5000);
        }
    }

    private void copyAssetsFile(Context context) {
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

    private void sh() {
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
                    String str = bufferedReader.readLine();
                    stringBuilder.append(str);
                    stringBuilder.append(System.lineSeparator());

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

    void overwriteFrp() {
        addText("- 通知：DchaService にバインドしています。");
        if (!bindService(new Intent("jp.co.benesse.dcha.dchaservice.DchaService").setPackage("jp.co.benesse.dcha.dchaservice"), new ServiceConnection() {

            @SuppressLint("SdCardPath")
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                IDchaService mDchaService = IDchaService.Stub.asInterface(iBinder);
                addText("- 通知：/dev/block/by-name/frp をコピーしています。");
                try {
                    mDchaService.copyUpdateImage("/dev/block/by-name/frp", "/cache/../sdcard/frp.bin");
                } catch (Exception e) {
                    addText("- 通知：エラーが発生しました。");
                    addText(e.toString());
                    init();
                    return;
                }

                try {
                    File file = new File(Environment.getExternalStorageDirectory(), "frp.bin");
                    DataInputStream dataInStream = new DataInputStream(new BufferedInputStream(new FileInputStream(new File(Environment.getExternalStorageDirectory(), "frp.bin"))));
                    DataOutputStream dataOutStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "tmp.bin"))));

                    int[] tmpHex = new int[(int) file.length()];
                    int i = 0;

                    addText("- 通知：frp.bin ファイルサイズ -> " + file.length());

                    while (true) {
                        int b = dataInStream.read();

                        if (b == -1) {
                            break;
                        }

                        tmpHex[i] = b;
                        i++;
                    }

                    tmpHex[tmpHex.length - 1] = 1;

                    for (int q : tmpHex) {
                        dataOutStream.write(q);
                    }

                    addText("- 通知：読込データ -> " + Arrays.toString(tmpHex));

                    dataInStream.close();
                    dataOutStream.close();

                    addText("- 通知：frp.bin の修正が完了しました。");
                    addText("- 通知：frp.bin を /dev/block/by-name/frp に上書きしています。");
                    mDchaService.copyUpdateImage("/sdcard/tmp.bin", "/cache/../dev/block/by-name/frp");

                    addText("- 通知：すべての操作が終了しました。");
                    addText("- 通知：ADB から bootloader モードを起動してブートローダをアンロックしてください。");

                    new AlertDialog.Builder(MainActivity.this)
                            .setCancelable(false)
                            .setTitle("開発者オプションを開きますか？")
                            .setMessage("続行すると、学習環境にして開発者オプションを開きます\nADB を有効にしたい場合は、開いてください\n\n注意：開発者向けオプションが有効になっていない場合は設定を開きます\n設定から開発者向けオプションを有効にして開いてください\nパスワード無しで開くことができます")
                            .setPositiveButton("OK", (dialog, which) -> {
                                try {
                                    mDchaService.setSetupStatus(3);
                                    if (Settings.Secure.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1) {
                                        startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                                    } else {
                                        startActivity(new Intent().setClassName("com.android.settings", "com.android.settings.Settings"));
                                    }
                                } catch (Exception ignored) {
                                }
                            })
                            .setNeutralButton("キャンセル", (dialog, which) -> dialog.dismiss())
                            .show();
                } catch (Exception e) {
                    addText("- 通知：エラーが発生しました。");
                    addText(e.toString());
                    init();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        }, Context.BIND_AUTO_CREATE)) {
            addText("- 通知：DchaService への接続に失敗しました。");
            init();
        }
    }

    void addText(String str) {
        TextView textView = findViewById(R.id.text);
        if (str.isEmpty()) {
            textView.append(System.lineSeparator());
        } else {
            textView.append(" " + str + System.lineSeparator());
        }
        ScrollView scrollView = findViewById(R.id.scroll);
        scrollView.fullScroll(View.FOCUS_DOWN);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
