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

    private static final String DCHA_PACKAGE = "jp.co.benesse.dcha.dchaservice";
    private static final String DCHA_SERVICE = DCHA_PACKAGE + ".DchaService";
    private static final String DigichalizedStatus = "dcha_state";
    private static final int DIGICHALIZE_STATUS_DIGICHALIZED = 3;
    private static final String DCHA_SYSTEM_COPY = "/cache/..";
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String SETTINGS_ACTIVITY = SETTINGS_PACKAGE + ".Settings";
    private static final String FRP_ORIGIN_PATH = "/dev/block/by-name/frp";
    private static final String FRP_FIXING_FILE = "frp.bin";
    private static final String FRP_FIXING_PATH = "/sdcard/" + FRP_FIXING_FILE;
    private static final String FRP_FIXING_TEMP = "tmp.bin";
    private static final String SHRINKER = "shrinker";
    private static final String SHRINKER_SUCCESS = "Permissive";
    private static final String SHRINKER_BIN_PATH = "/data/data/com.saradabar.easyblu/files/";

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

    @Deprecated
    void init() {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("実行しますか？")
                .setMessage("続行するには OK を押下してください\n\nキャンセルを押すと Android 設定に遷移します")
                .setPositiveButton("OK", (dialog, which) -> {
                    addText("- 通知：" + SHRINKER + " を実行しました");
                    addText("- 警告：デバイスには絶対に触れないでください。処理が終了するまでお待ち下さい。");
                    addText("- 警告：デバイスが再起動した場合は失敗です。起動後に再度実行してください。");
                    new Handler().postDelayed(() -> {
                        String result = shrinker();
                        if (result.contains(SHRINKER_SUCCESS)) {
                            addText("- 通知：成功しました。");
                            addText("- 通知：" + FRP_FIXING_FILE + " の修正を試みます。");
                            new Handler().postDelayed(this::overwriteFrp, 5000);
                        } else {
                            addText("- 通知：失敗しました。再度実行します。");
                            new Handler().postDelayed(this::retry, 5000);
                        }
                    }, 5000);
                })
                .setNegativeButton("キャンセル", (dialog, which) -> {
                    try {
                        Settings.System.putInt(getContentResolver(), DigichalizedStatus, DIGICHALIZE_STATUS_DIGICHALIZED);
                    } catch (Exception ignored) {
                    }
                    startActivity(new Intent().setClassName(SETTINGS_PACKAGE, SETTINGS_ACTIVITY));
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
        execute("chmod +x " + new File(getFilesDir(), SHRINKER).getAbsolutePath());
        execute(SHRINKER_BIN_PATH + SHRINKER);
        String text = getText().toString();
        addText("- 結果：");
        addText(text);
        return text;
    }

    @SuppressLint("SdCardPath")
    @Deprecated
    void retry() {
        execute(SHRINKER_BIN_PATH + SHRINKER);
        String text = getText().toString();
        addText("- 結果:");
        addText(text);
        if (text.contains(SHRINKER_SUCCESS)) {
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
            InputStream inputStream = context.getAssets().open(SHRINKER);
            FileOutputStream fileOutputStream = new FileOutputStream(new File(context.getFilesDir(), SHRINKER), false);
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
        if (!bindService(new Intent(DCHA_SERVICE).setPackage(DCHA_PACKAGE), new ServiceConnection() {

            @SuppressLint("SdCardPath")
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                IDchaService mDchaService = IDchaService.Stub.asInterface(iBinder);
                addText("- 通知：" + FRP_ORIGIN_PATH + " をコピーしています。");
                try {
                    mDchaService.copyUpdateImage(FRP_ORIGIN_PATH, DCHA_SYSTEM_COPY + FRP_FIXING_PATH);
                } catch (Exception e) {
                    addText("- 通知：FRP のコピーに失敗しました。");
                    addText(e.toString());
                    init();
                    return;
                }

                try {
                    File file = new File(Environment.getExternalStorageDirectory(), FRP_FIXING_FILE);
                    DataInputStream dataInStream = new DataInputStream(new BufferedInputStream(new FileInputStream(new File(Environment.getExternalStorageDirectory(), FRP_FIXING_FILE))));
                    DataOutputStream dataOutStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(Environment.getExternalStorageDirectory(), FRP_FIXING_TEMP))));

                    int[] tmpHex = new int[(int) file.length()];
                    int i = 0;

                    addText("- 通知：" + FRP_FIXING_FILE + " ファイルサイズ -> " + file.length());

                    while ((tmpHex[i] = dataInStream.read()) != -1) {
                        i++;
                    }

                    tmpHex[tmpHex.length - 1] = 1;

                    for (int q : tmpHex) {
                        dataOutStream.write(q);
                    }

                    addText("- 通知：読込データ -> " + Arrays.toString(tmpHex));

                    dataInStream.close();
                    dataOutStream.close();

                    addText("- 通知：" + FRP_FIXING_FILE + " の修正が完了しました。");
                    addText("- 通知：" + FRP_FIXING_FILE + " を " + FRP_ORIGIN_PATH + " に上書きしています。");
                    mDchaService.copyUpdateImage(FRP_FIXING_PATH, DCHA_SYSTEM_COPY + FRP_ORIGIN_PATH);

                    addText("- 通知：すべての操作が終了しました。");
                    addText("- 通知：ADB から bootloader モードを起動してブートローダをアンロックしてください。");

                    new AlertDialog.Builder(MainActivity.this)
                            .setCancelable(false)
                            .setTitle("開発者オプションを開きますか？")
                            .setMessage("続行すると、学習環境にして開発者オプションを開きます\nADB を有効にしたい場合は、開いてください\n\n注意：開発者向けオプションが有効になっていない場合は設定を開きます\n設定から開発者向けオプションを有効にして開いてください\nパスワード無しで開くことができます")
                            .setPositiveButton("OK", (dialog, which) -> {
                                try {
                                    mDchaService.setSetupStatus(3);
                                    startActivity(
                                        Settings.Secure.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
                                            ? new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                            : new Intent().setClassName(SETTINGS_PACKAGE, SETTINGS_ACTIVITY)
                                    );
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
        textView.append(str.isEmpty() ? System.lineSeparator() : " " + str + System.lineSeparator());
        ScrollView scrollView = findViewById(R.id.scroll);
        scrollView.fullScroll(View.FOCUS_DOWN);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
