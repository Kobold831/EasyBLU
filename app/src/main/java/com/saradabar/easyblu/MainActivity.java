package com.saradabar.easyblu;

import android.app.Activity;
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
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

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

    private StringBuilder exec(String str) {
        Process process;
        BufferedWriter bufferedWriter;
        BufferedReader bufferedReader;
        StringBuilder stringBuilder = new StringBuilder();

        try {
            process = Runtime.getRuntime().exec(str + System.lineSeparator());
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            bufferedWriter.write("exit" + System.lineSeparator());
            bufferedWriter.flush();
            process.waitFor();

            String data;
            while ((data = bufferedReader.readLine()) != null) {
                stringBuilder.append(data).append(System.lineSeparator());
            }
            echo(stringBuilder.toString());
        } catch (Exception e) {
            echo("エラーが発生しました" + System.lineSeparator() + e);
        }
        return stringBuilder;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
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

    private void echo(String str) {
        TextView textView = findViewById(R.id.text_console);
        textView.append(str + System.lineSeparator());
        ScrollView scrollView = findViewById(R.id.scroll_console);
        scrollView.fullScroll(View.FOCUS_DOWN);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        echo("****************************");
        echo("Welcome to Easy BLU ! :)");
        echo("Easy BLU へようこそ！");
        echo("****************************");
        echo("fingerprint：" + Build.FINGERPRINT);
        init();
    }

    private void init() {
        TextView textView = findViewById(R.id.text_status);
        textView.setText("実行しますか？続行するには 実行 を押下してください\nキャンセル を押すと Android 設定に遷移します");
        Button mainButton = findViewById(R.id.button_main);
        Button subButton = findViewById(R.id.button_sub);
        mainButton.setEnabled(true);
        mainButton.setText("実行");
        mainButton.setOnClickListener(v -> {
            mainButton.setEnabled(false);
            subButton.setEnabled(false);
            textView.setText("デバイスには絶対に触れないでください。処理が終了するまでお待ち下さい。\nデバイスが再起動した場合は失敗です。起動後に再度実行してください。");

            echo("- 通知：" + (Build.PRODUCT.equals(CT3) ? MTK_SU : SHRINKER) + " を実行しました");
            echo("- 警告：デバイスには絶対に触れないでください。処理が終了するまでお待ち下さい。");
            echo("- 警告：デバイスが再起動した場合は失敗です。起動後に再度実行してください。");

            new Handler().postDelayed(() -> {
                String result = Build.PRODUCT.equals(CT3) ? mtkSu() : shrinker();
                if (result.contains(SHRINKER_SUCCESS)) {
                    echo("- 通知：成功しました。");
                    if (Build.PRODUCT.equals(CT3)) {
                        echo("- 通知：expdb のサイズを計算します。");
                        checkFixed();
                    } else {
                        echo(FRP_FIXING_FILE + " の修正を試みます。");
                        overwriteFrp();
                    }
                } else {
                    echo("- 通知：失敗しました。再度実行します。");
                    if (Build.PRODUCT.equals(CT3)) {
                        retryMtkSu();
                    } else {
                        retryShrinker();
                    }
                }
            }, DELAY_MS);
        });
        subButton.setEnabled(true);
        subButton.setText("キャンセル");
        subButton.setOnClickListener(v -> {
            try {
                Settings.System.putInt(getContentResolver(), DCHA_STATE, DIGICHALIZE_STATUS_DIGICHALIZED);
                startActivity(new Intent().setClassName(SETTINGS_PACKAGE, SETTINGS_ACTIVITY));
            } catch (Exception e) {
                echo("エラーが発生しました" + System.lineSeparator() + e);
            }
        });
    }

    String shrinker() {
        echo("- 通知：" + getFilesDir() + " にファイルをコピーしています。");
        copyAssetsFile(this, SHRINKER);
        echo("- 結果：");
        return exec(APP_PATH + SHRINKER).toString();
    }

    String mtkSu() {
        echo("- 通知：" + getFilesDir() + " にファイルをコピーしています。");
        copyAssetsFile(this, MTK_SU);
        echo("- 結果：");
        return exec(APP_PATH + MTK_SU + " getenforce").toString();
    }

    void retryShrinker() {
        echo("- 結果:");
        String text = exec(APP_PATH + SHRINKER).toString();
        if (text.contains(SHRINKER_SUCCESS)) {
            echo("- 通知：成功しました。");
            echo("- 通知：frp.bin の修正を試みます。");
            overwriteFrp();
        } else {
            echo("- 通知：失敗しました。再度実行します。");
            retryShrinker();
        }
    }

    void retryMtkSu() {
        echo("- 結果:");
        String text = exec("sh " + APP_PATH + MTK_SU).toString();
        if (text.contains(SHRINKER_SUCCESS)) {
            echo("- 通知：成功しました。");
            echo("- 通知：expdb のサイズを計算します。");
            checkFixed();
        } else {
            echo("- 通知：失敗しました。再度実行します。");
            retryMtkSu();
        }
    }

    void overwriteFrp() {
        echo("- 通知：DchaService にバインドしています。");
        if (!bindService(new Intent(DCHA_SERVICE).setPackage(DCHA_PACKAGE), new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                IDchaService mDchaService = IDchaService.Stub.asInterface(iBinder);
                echo("- 通知：" + FRP_ORIGIN_PATH + " をコピーしています。");
                try {
                    mDchaService.copyUpdateImage(FRP_ORIGIN_PATH, DCHA_SYSTEM_COPY + FRP_FIXING_PATH);
                } catch (Exception e) {
                    echo("- 通知：FRP のコピーに失敗しました。");
                    echo("エラーが発生しました" + System.lineSeparator() + e);
                    init();
                    return;
                }

                try {
                    File file = new File(Environment.getExternalStorageDirectory(), FRP_FIXING_FILE);
                    DataInputStream dataInStream = new DataInputStream(new BufferedInputStream(new FileInputStream(new File(Environment.getExternalStorageDirectory(), FRP_FIXING_FILE))));
                    DataOutputStream dataOutStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(Environment.getExternalStorageDirectory(), FRP_FIXING_TEMP))));

                    int[] tmpHex = new int[(int) file.length()];
                    int i = 0;

                    echo("- 通知：" + FRP_FIXING_FILE + " ファイルサイズ -> " + file.length());

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

                    //echo("- 通知：読込データ -> " + Arrays.toString(tmpHex));

                    dataInStream.close();
                    dataOutStream.close();

                    echo("- 通知：" + FRP_FIXING_FILE + " の修正が完了しました。");
                    echo("- 通知：" + FRP_FIXING_FILE + " を " + FRP_ORIGIN_PATH + " に上書きしています。");
                    mDchaService.copyUpdateImage(FRP_FIXING_PATH, DCHA_SYSTEM_COPY + FRP_ORIGIN_PATH);

                    openSettings();
                } catch (Exception e) {
                    echo("エラーが発生しました" + System.lineSeparator() + e);
                    init();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        }, Context.BIND_AUTO_CREATE)) {
            echo("- 通知：DchaService への接続に失敗しました。");
            init();
        }
    }

    void checkFixed() {
        if (getExpdbSize().contains("124MB   134MB")) { // expdb のセクタ範囲
            echo("- 通知：expdb は修正されていません。");
            fixExpdb();
        } else {
            echo("- 通知：expdb は既に修正済みです。");
            openSettings();
        }
    }

    String getExpdbSize() {
        copyAssetsFile(this, PARTED);
        echo("- 結果：");
        return exec("sh " + PARTED_CMD + "print").toString();
    }

    void fixExpdb() {
        echo("- 通知：expdb を削除します。");
        exec(PARTED_CMD + "rm 13");
        echo("- 通知：expdb を 9MB で再生成します。");
        exec(PARTED_CMD + "mkpart expdb 124MB 133MB");
        echo("- 通知：expdb のラベルを設定します。");
        exec(PARTED_CMD + "name 13 expdb");
        echo("- 通知：expdb のフラグを修正します。");
        echo("- 結果：");
        exec(PARTED_CMD + "toggle 13 msftdata");
        createFrp();
    }

    void createFrp() {
        echo("- 通知：frp を 1MB で生成します。");
        exec("sh " + PARTED_CMD + "mkpart frp 133MB 134MB");
        echo("- 通知：frp のラベルを設定します。");
        exec("sh " + PARTED_CMD + "name 24 frp");
        echo("- 通知：frp のフラグを修正します。");
        exec("sh " + PARTED_CMD + "toggle 24 msftdata");
        echo("- 通知：frp を修正します。");
        copyAssetsFile(this, FRP);
        echo("- 結果：");
        exec("sh " + "dd if=" + FRP + " of=" + MMCBLK0 + "p24");
        openSettings();
    }

    void openSettings() {
        echo("- 通知：すべての操作が終了しました。");
        echo("- 通知：ADB から bootloader モードを起動してブートローダをアンロックしてください。");

        TextView textView = findViewById(R.id.text_status);
        textView.setText("開発者オプションを開きますか？続行すると、学習環境にして開発者オプションを開きます\nADB を有効にしたい場合は、開いてください\n注意：開発者向けオプションが有効になっていない場合は設定を開きます\n設定から開発者向けオプションを有効にして開いてください\nパスワード無しで開くことができます");
        Button mainButton = findViewById(R.id.button_main);
        Button subButton = findViewById(R.id.button_sub);
        mainButton.setEnabled(true);
        mainButton.setText("開く");
        mainButton.setOnClickListener(v -> {
            try {
                Settings.System.putInt(getContentResolver(), DCHA_STATE, DIGICHALIZE_STATUS_DIGICHALIZED);
                startActivity(
                        Settings.Secure.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
                                ? new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                : new Intent().setClassName(SETTINGS_PACKAGE, SETTINGS_ACTIVITY)
                );
            } catch (Exception e) {
                echo("エラーが発生しました" + System.lineSeparator() + e);
            }
        });
        subButton.setEnabled(true);
        subButton.setText("キャンセル");
        subButton.setOnClickListener(null);
    }
}
