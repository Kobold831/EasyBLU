package com.saradabar.easyblu;

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

    private static final int DELAY_MS = 800;
    private static final String CT3 = "TAB-A04-BR3";
    private static final String MMCBLK0 = "/dev/block/mmcblk0";
    private static final String APP_PATH = "/data/data/com.saradabar.easyblu/files/";
    private static final String DCHA_PACKAGE = "jp.co.benesse.dcha.dchaservice";
    private static final String DCHA_SERVICE = DCHA_PACKAGE + ".DchaService";
    private static final String DCHA_STATE = "dcha_state";
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
    private static final String MTK_SU = "mtk-su";
    private static final String PARTED = "parted";
    private static final String PARTED_CMD = APP_PATH + PARTED + " -s " + MMCBLK0 + " ";
    private static final String FRP = "frp";

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
        //noinspection deprecation
        init();
    }

    @Deprecated
    void init() {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("実行しますか？")
                .setMessage("続行するには OK を押下してください\n\nキャンセルを押すと Android 設定に遷移します")
                .setPositiveButton("OK", (dialog, which) -> {
                    addText("- 通知：" +  (Build.PRODUCT.equals(CT3) ? MTK_SU : SHRINKER) + " を実行しました");
                    addText("- 警告：デバイスには絶対に触れないでください。処理が終了するまでお待ち下さい。");
                    addText("- 警告：デバイスが再起動した場合は失敗です。起動後に再度実行してください。");

                    new Handler().postDelayed(() -> {
                        String result = Build.PRODUCT.equals(CT3) ? mtkSu() : shrinker();
                        if (result.contains(SHRINKER_SUCCESS)) {
                            addText("- 通知：成功しました。");
                            addText("- 通知：" + (Build.PRODUCT.equals(CT3) ? "expdb のサイズを計算します。" : (FRP_FIXING_FILE + " の修正を試みます。")));
                            new Handler().postDelayed(Build.PRODUCT.equals(CT3) ? this::checkFixed : this::overwriteFrp, DELAY_MS);
                        } else {
                            addText("- 通知：失敗しました。再度実行します。");
                            //noinspection deprecation
                            new Handler().postDelayed(Build.PRODUCT.equals(CT3) ? this::retryMtkSu : this::retryShrinker, DELAY_MS);
                        }
                    }, DELAY_MS);

                })
                .setNegativeButton("キャンセル", (dialog, which) -> {
                    try {
                        Settings.System.putInt(getContentResolver(), DCHA_STATE, DIGICHALIZE_STATUS_DIGICHALIZED);
                    } catch (Exception ignored) {
                    }
                    startActivity(new Intent().setClassName(SETTINGS_PACKAGE, SETTINGS_ACTIVITY));
                })
                .show();
    }

    String shrinker() {
        stringBuilder = new StringBuilder();
        addText("- 通知：" + getFilesDir() + " にファイルをコピーしています。");
        copyAssetsFile(this, SHRINKER);
        sh("sh");
        execute(APP_PATH + SHRINKER);
        String text = getText().toString();
        addText("- 結果：");
        addText(text);
        return text;
    }

    String mtkSu() {
        stringBuilder = new StringBuilder();
        addText("- 通知：" + getFilesDir() + " にファイルをコピーしています。");
        copyAssetsFile(this, MTK_SU);
        sh(APP_PATH + MTK_SU);
        execute("getenforce");
        String text = getText().toString();
        addText("- 結果：");
        addText(text);
        return text;
    }

    @Deprecated
    void retryShrinker() {
        execute(APP_PATH + SHRINKER);
        String text = getText().toString();
        addText("- 結果:");
        addText(text);
        if (text.contains(SHRINKER_SUCCESS)) {
            addText("- 通知：成功しました。");
            addText("- 通知：frp.bin の修正を試みます。");
            new Handler().postDelayed(this::overwriteFrp, DELAY_MS);
        } else {
            addText("- 通知：失敗しました。再度実行します。");
            //noinspection deprecation
            new Handler().postDelayed(this::retryShrinker, DELAY_MS);
        }
    }

    @Deprecated
    void retryMtkSu() {
        execute(APP_PATH + MTK_SU);
        String text = getText().toString();
        addText("- 結果:");
        addText(text);
        if (text.contains(SHRINKER_SUCCESS)) {
            addText("- 通知：成功しました。");
            addText("- 通知：expdb のサイズを計算します。");
            new Handler().postDelayed(this::checkFixed, DELAY_MS);
        } else {
            addText("- 通知：失敗しました。再度実行します。");
            //noinspection deprecation
            new Handler().postDelayed(this::retryMtkSu, DELAY_MS);
        }
    }

    private void copyAssetsFile(Context context, String file) {
        File bin = new File(context.getFilesDir(), file);
        try {
            InputStream inputStream = context.getAssets().open(file);
            FileOutputStream fileOutputStream = new FileOutputStream(bin, false);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) >= 0) {
                fileOutputStream.write(buffer, 0, length);
            }
            bin.setExecutable(true);
            fileOutputStream.close();
            inputStream.close();
        } catch (IOException ignored) {
        }
    }

    private void sh(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec(cmd);
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

            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                IDchaService mDchaService = IDchaService.Stub.asInterface(iBinder);
                addText("- 通知：" + FRP_ORIGIN_PATH + " をコピーしています。");
                try {
                    mDchaService.copyUpdateImage(FRP_ORIGIN_PATH, DCHA_SYSTEM_COPY + FRP_FIXING_PATH);
                } catch (Exception e) {
                    addText("- 通知：FRP のコピーに失敗しました。");
                    addText(e.toString());
                    //noinspection deprecation
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

                    openSettings();
                } catch (Exception e) {
                    addText("- 通知：エラーが発生しました。");
                    addText(e.toString());
                    //noinspection deprecation
                    init();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        }, Context.BIND_AUTO_CREATE)) {
            addText("- 通知：DchaService への接続に失敗しました。");
            //noinspection deprecation
            init();
        }
    }

    void checkFixed() {
        if (getExpdbSize().contains("124MB   134MB")) { // expdb のセクタ範囲
            addText("- 通知：expdb は修正されていません。");
            new Handler().postDelayed(this::fixExpdb, DELAY_MS);
        } else {
            addText("- 通知：expdb は既に修正済みです。");
            new Handler().postDelayed(this::openSettings, DELAY_MS);
        }
    }

    String getExpdbSize() {
        stringBuilder = new StringBuilder();
        copyAssetsFile(this, PARTED);
        execute(PARTED_CMD + "print");
        String text = getText().toString();
        addText("- 結果：");
        addText(text);
        return text;
    }

    void fixExpdb() {
        stringBuilder = new StringBuilder();
        addText("- 通知：expdb を削除します。");
        execute(PARTED_CMD + "rm 13");
        addText("- 通知：expdb を 9MB で再生成します。");
        execute(PARTED_CMD + "mkpart expdb 124MB 133MB");
        addText("- 通知：expdb のラベルを設定します。");
        execute(PARTED_CMD + "name 13 expdb");
        addText("- 通知：expdb のフラグを修正します。");
        execute(PARTED_CMD + "toggle 13 msftdata");
        String text = getText().toString();
        addText("- 結果：");
        addText(text);
        new Handler().postDelayed(this::createFrp, DELAY_MS);
    }

    void createFrp() {
        stringBuilder = new StringBuilder();
        addText("- 通知：frp を 1MB で生成します。");
        execute(PARTED_CMD + "mkpart frp 133MB 134MB");
        addText("- 通知：frp のラベルを設定します。");
        execute(PARTED_CMD + "name 24 frp");
        addText("- 通知：frp のフラグを修正します。");
        execute(PARTED_CMD + "toggle 24 msftdata");
        addText("- 通知：frp を修正します。");
        copyAssetsFile(this, FRP);
        execute("dd if=" + FRP + " of=" + MMCBLK0 + "p24");
        String text = getText().toString();
        addText("- 結果：");
        addText(text);
        new Handler().postDelayed(this::openSettings, DELAY_MS);
    }

    void openSettings() {
        addText("- 通知：すべての操作が終了しました。");
        addText("- 通知：ADB から bootloader モードを起動してブートローダをアンロックしてください。");

        new AlertDialog.Builder(MainActivity.this)
                .setCancelable(false)
                .setTitle("開発者オプションを開きますか？")
                .setMessage("続行すると、学習環境にして開発者オプションを開きます\nADB を有効にしたい場合は、開いてください\n\n注意：開発者向けオプションが有効になっていない場合は設定を開きます\n設定から開発者向けオプションを有効にして開いてください\nパスワード無しで開くことができます")
                .setPositiveButton("OK", (dialog, which) -> {
                    try {
                        Settings.System.putInt(getContentResolver(), DCHA_STATE, DIGICHALIZE_STATUS_DIGICHALIZED);
                    } catch (Exception ignored) {
                    }
                    startActivity(
                            Settings.Secure.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
                                    ? new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                    : new Intent().setClassName(SETTINGS_PACKAGE, SETTINGS_ACTIVITY)
                    );
                })
                .setNeutralButton("キャンセル", (dialog, which) -> dialog.dismiss())
                .show();
    }

    void addText(String str) {
        TextView textView = findViewById(R.id.text);
        textView.append(str.isEmpty() ? System.lineSeparator() : " " + str + System.lineSeparator());
        ScrollView scrollView = findViewById(R.id.scroll);
        scrollView.fullScroll(View.FOCUS_DOWN);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

}
