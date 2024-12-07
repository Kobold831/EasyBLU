package com.saradabar.easyblu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

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

import jp.co.benesse.dcha.dchaservice.IDchaService;

public class MainActivity extends Activity {

    private static final int DELAY_MS = 800;
    private static final boolean CT3 = Build.PRODUCT.equals("TAB-A04-BR3");
    private static final String DCHA_PACKAGE = "jp.co.benesse.dcha.dchaservice";
    private static final String DCHA_SERVICE = DCHA_PACKAGE + ".DchaService";
    private static final int DIGICHALIZE_STATUS_DIGICHALIZED = 3;
    private static final String DCHA_SYSTEM_COPY = "/cache/..";
    private static final int DCHA_REBOOT_RECOVERY = 1;
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String SETTINGS_ACTIVITY = SETTINGS_PACKAGE + ".Settings";
    private static final String MMCBLK0 = "/dev/block/mmcblk0";
    private static final String FRP_ORIGIN_PATH = "/dev/block/by-name/frp";
    private static final String APP_PATH = "/data/data/com.saradabar.easyblu/files/";
    private static final String FRP_FIXING_FILE = "frp.bin";
    private static final String FRP_FIXING_TEMP = "tmp.bin";
    private static final String SHRINKER = "mali_shrinker_mmap32";
    private static final String PERMISSIVE = "Permissive";
    private static final String MTK_SU = "mtk-su";
    private static final String PARTED = "parted";
    private static final String FRP = "frp";

    private static IDchaService mDchaService = null;
    private static DataOutputStream dos;
    private static BufferedReader bufferedReader, bufferedReaderIgnored;
    private static final StringBuilder stringBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        addText("""
                ****************************

                  Welcome to Easy BLU ! :)
                     Easy BLU へようこそ！
                
                ****************************""");
        addText("fingerprint：" + Build.FINGERPRINT);
        init();
    }

    private void callFunc(Runnable func) {
        new Handler(Looper.getMainLooper()).postDelayed(func, DELAY_MS);
    }

    private void init() {
        new AlertDialog.Builder(this)
            .setCancelable(false)
            .setTitle("実行しますか？")
            .setMessage("""
                続行するには OK を押下してください
                キャンセルを押すと Android 設定に遷移します""")
            .setPositiveButton("OK", (dialog, which) -> {
                addText("- 通知：" +  (CT3 ? MTK_SU : SHRINKER) + " を実行しました");
                addText("- 警告：デバイスには絶対に触れないでください。処理が終了するまでお待ち下さい。");
                addText("- 警告：デバイスが再起動した場合は失敗です。起動後に再度実行してください。");
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (getenforce()) {
                        addText("- 通知：成功しました。");
                        addText("- 通知：" + (CT3 ? "expdb のサイズを計算します。" : (FRP + " の修正を試みます。")));
                        callFunc(CT3 ? this::checkFixed : this::overwriteFrp);
                    } else {
                        addText("- 通知：失敗しました。再度実行します。");
                        callFunc(this::retry);
                    }
                }, DELAY_MS);
            })
            .setNegativeButton("キャンセル", (dialog, which) -> {
                setDigichalized();
                startActivity(new Intent().setClassName(SETTINGS_PACKAGE, SETTINGS_ACTIVITY));
            })
            .show();
    }

    private boolean getenforce() {
        copyAssetsFile(CT3 ? MTK_SU : SHRINKER);
        sh(CT3 ? MTK_SU : SHRINKER);
        execute("getenforce");
        String text = getText().toString();
        addText("- 結果：");
        addText(text);
        return text.contains(PERMISSIVE);
    }

    private void retry() {
        execute(CT3 ? MTK_SU : SHRINKER);
        String text = getText().toString();
        addText("- 結果:");
        addText(text);
        if (getenforce()) {
            addText("- 通知：成功しました。");
            addText("- 通知：" + (CT3 ? "expdb のサイズを計算します。" : FRP + " の修正を試みます。"));
            callFunc(CT3 ? this::checkFixed : this::overwriteFrp);
        } else {
            addText("- 通知：失敗しました。再度実行します。");
            callFunc(this::retry);
        }
    }

    private void copyAssetsFile(String file) {
        addText("- 通知：" + getFilesDir() + " に " + (file) + " をコピーしています。");
        File bin = new File(getFilesDir(), file);
        try {
            InputStream inputStream = getAssets().open(file);
            FileOutputStream fileOutputStream = new FileOutputStream(bin, false);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) >= 0) fileOutputStream.write(buffer, 0, length);
            //noinspection ResultOfMethodCallIgnored
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
            bufferedReaderIgnored = new BufferedReader(new InputStreamReader(new DataInputStream(process.getErrorStream())));
        } catch (Exception ignored) {
        }
    }

    private void execute(String str) {
        try {
            dos.writeBytes((str.contains("dd") ? "" : APP_PATH) + str + "\n");
            dos.flush();
        } catch (Exception ignored) {
        }
    }

    private StringBuilder getText() {
        try {
            int i = 0;
            stringBuilder.setLength(0);
            while (i <= 500) {
                if (bufferedReader.ready()) {
                    String str = bufferedReader.readLine();
                    if (str == null) break;
                    stringBuilder.append(str);
                    stringBuilder.append(System.lineSeparator());
                    i = 0;
                } else {
                    try {
                        Thread.sleep(10);
                    } catch (Exception ignored) {
                        Thread.currentThread().interrupt();
                    }
                    i++;
                }
            }
        } catch (Exception ignored) {
        }
        return stringBuilder;
    }

    private void addText(@NonNull String str) {
        TextView textView = findViewById(R.id.text);
        textView.append(str.isEmpty() ? System.lineSeparator() : " " + str + System.lineSeparator());
        ScrollView scrollView = findViewById(R.id.scroll);
        scrollView.fullScroll(View.FOCUS_DOWN);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }


    private void parted(String cmd) {
        execute(PARTED + " -s " + MMCBLK0 + " " + cmd);
    }

    private void overwriteFrp() {
        addText("- 通知：" + FRP_ORIGIN_PATH + " をコピーしています。");
        try {
            copyFile(FRP_ORIGIN_PATH, APP_PATH + FRP_FIXING_FILE);
        } catch (Exception e) {
            addText("- 通知：" + FRP_ORIGIN_PATH + " のコピーに失敗しました。");
            addText(e.toString());
            init();
            return;
        }
        try {
            File file = new File(APP_PATH, FRP_FIXING_FILE);
            DataInputStream dataInStream = new DataInputStream(new BufferedInputStream(new FileInputStream(new File(APP_PATH, FRP_FIXING_FILE))));
            DataOutputStream dataOutStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(APP_PATH, FRP_FIXING_TEMP))));
            int[] tmpHex = new int[(int) file.length()];
            addText("- 通知：" + FRP_FIXING_FILE + " ファイルサイズ -> " + file.length());
            int i = 0;
            while ((tmpHex[i] = dataInStream.read()) != -1) i++;
            tmpHex[tmpHex.length - 1] = 1;
            for (int q : tmpHex) dataOutStream.write(q);
            dataInStream.close();
            dataOutStream.close();
            //addText("- 通知：読込データ -> " + Arrays.toString(tmpHex));
            addText("- 通知：" + FRP_FIXING_FILE + " の修正が完了しました。");
            addText("- 通知：" + FRP_FIXING_FILE + " を " + FRP_ORIGIN_PATH + " に上書きしています。");
            copyFile(APP_PATH + FRP_FIXING_FILE, FRP_ORIGIN_PATH);

            openSettings();
        } catch (Exception e) {
            addText("- 通知：エラーが発生しました。");
            addText(e.toString());
            init();
        }
    }

    private void checkFixed() {
        if (getExpdbSize().contains("124MB   134MB")) { // expdb のセクタ範囲
            addText("- 通知：expdb は修正されていません。");
            callFunc(this::fixExpdb);
        } else {
            addText("- 通知：expdb は既に修正済みです。");
            callFunc(this::openSettings);
        }
    }

    @NonNull
    private String getExpdbSize() {
        copyAssetsFile(PARTED);
        parted("print");
        String text = getText().toString();
        addText("- 結果：");
        addText(text);
        return text;
    }

    private void fixExpdb() {
        addText("- 通知：expdb を削除します。");
        parted("rm 13");
        addText("- 通知：expdb を 9MB で再生成します。");
        parted("mkpart expdb 124MB 133MB");
        addText("- 通知：expdb のラベルを設定します。");
        parted("name 13 expdb");
        addText("- 通知：expdb のフラグを修正します。");
        parted("toggle 13 msftdata");
        String text = getText().toString();
        addText("- 結果：");
        addText(text);
        callFunc(this::createFrp);
    }

    private void createFrp() {
        addText("- 通知：frp を 1MB で生成します。");
        parted("mkpart frp 133MB 134MB");
        addText("- 通知：frp のラベルを設定します。");
        parted("name 24 frp");
        addText("- 通知：frp のフラグを修正します。");
        parted("toggle 24 msftdata");
        addText("- 通知：frp を修正します。");
        copyAssetsFile(FRP);
        execute("dd if=" + FRP + " of=" + MMCBLK0 + "p24");
        String text = getText().toString();
        addText("- 結果：");
        addText(text);
        callFunc(this::openSettings);
    }

    private void runReset() {
        new AlertDialog.Builder(MainActivity.this)
            .setTitle("初期化しますか？")
            .setMessage("初期化後、もう一度、EasyBLU を実行してください")
            .setPositiveButton("実行", (dialog, which) -> rebootWipeUserData())
            .setNegativeButton("キャンセル", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private void openSettings() {
        addText("- 通知：すべての操作が終了しました。");
        addText("- 通知：ADB から bootloader モードを起動してブートローダをアンロックしてください。");
        addText("$ adb reboot bootloader");
        addText("$ fastboot flashing unlock");

        new AlertDialog.Builder(MainActivity.this)
            .setCancelable(false)
            .setTitle("開発者オプションを開きますか？")
            .setMessage("""
                続行すると、開発者オプションを開きます
                ADB より bootloader を起動して、ブートローダーアンロックしてください
                設定アプリが起動した場合は表示を有効にしてください

                ※このアプリの実行が１回目の場合、１度初期化してください
                詳しくは GitHub を参照してください""")
            .setPositiveButton("OK", (dialog, which) -> {
                setDigichalized();
                startActivity(
                    Settings.Secure.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
                        ? new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        : new Intent().setClassName(SETTINGS_PACKAGE, SETTINGS_ACTIVITY)
                );
            })
            .setNeutralButton("初期化", (dialog, which) -> runReset())
            .setNegativeButton("キャンセル", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private void copyFile(String src, String dst) {
        if (!bindService(new Intent(DCHA_SERVICE).setPackage(DCHA_PACKAGE), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                mDchaService = IDchaService.Stub.asInterface(iBinder);
                try {
                    mDchaService.copyUpdateImage(src, DCHA_SYSTEM_COPY + dst);
                } catch (RemoteException ignored) {
                }
                unbindService(this);
            }
            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        }, Context.BIND_AUTO_CREATE)) {
            addText("- 通知：DchaService への接続に失敗しました。");
            init();
        }
    }

    private void rebootWipeUserData() {
        if (!bindService(new Intent(DCHA_SERVICE).setPackage(DCHA_PACKAGE), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                mDchaService = IDchaService.Stub.asInterface(iBinder);
                try {
                    mDchaService.rebootPad(DCHA_REBOOT_RECOVERY, null);
                } catch (RemoteException ignored) {
                }
                unbindService(this);
            }
            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        }, Context.BIND_AUTO_CREATE)) {
            addText("- 通知：DchaService への接続に失敗しました。");
            init();
        }
    }

    private void setDigichalized() {
        if (!bindService(new Intent(DCHA_SERVICE).setPackage(DCHA_PACKAGE), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                mDchaService = IDchaService.Stub.asInterface(iBinder);
                try {
                    mDchaService.setSetupStatus(DIGICHALIZE_STATUS_DIGICHALIZED);
                } catch (RemoteException ignored) {
                }
                unbindService(this);
            }
            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        }, Context.BIND_AUTO_CREATE)) {
            addText("- 通知：DchaService への接続に失敗しました。");
            init();
        }
    }

}
