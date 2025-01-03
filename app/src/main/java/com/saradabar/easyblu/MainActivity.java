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
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

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

    private static final int DELAY_MS = 600; // 0.6 秒の遅延
    private static final boolean CT3 = Build.PRODUCT.equals("TAB-A04-BR3"); // CT3 かどうかの真偽値
    private static final String MMCBLK0 = "/dev/block/mmcblk0"; // 内部ストレージ
    private static final String PART24 = MMCBLK0 + "p24"; // CT3 で新規パーティションを作成した際の割振番号
    private static final String APP_PATH = "/data/data/com.saradabar.easyblu/cache/"; // getCacheDir() + "/" と同様
    private static final String DCHA_PACKAGE = "jp.co.benesse.dcha.dchaservice";
    private static final String DCHA_SERVICE = DCHA_PACKAGE + ".DchaService"; // copyUpdateImage を使ってシステム権限でファイルを操作
    private static final String DCHA_STATE = "dcha_state";
    private static final int DIGICHALIZE_STATUS_DIGICHALIZED = 3; // 開発者向けオプションのロック(BenesseExtension.checkPassword)の阻止
    private static final String DCHA_SYSTEM_COPY = "/cache/.."; // 内部の if 文で弾かれるのを防ぐ
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String SETTINGS_ACTIVITY = SETTINGS_PACKAGE + ".Settings"; // 設定アプリのメインアクティビティ
    private static final String FRP_ORIGIN_PATH = "/dev/block/platform/bootdevice/by-name/frp"; // ro.frp.pst と同様
    private static final String FRP_FIXING_FILE = "frp.tmp"; // オリジナルをコピー
    private static final String FRP_FIXING_PATH = Environment.getExternalStorageDirectory() + "/" + FRP_FIXING_FILE;
    private static final String FRP_FIXED_FILE = "frp.bin"; // 0xFFFFF を 0x01 に書き換えたもの
    private static final String FRP_FIXED_PATH = Environment.getExternalStorageDirectory() + "/" + FRP_FIXED_FILE;
    private static final String SHRINKER = "shrinker"; // 純正 boot の CTX/CTZ 専用。`getenforce` を実行
    private static final String PERMISSIVE = "Permissive"; // デバイスを書き換えられるかどうかの確認
    private static final String MTK_SU = "mtk-su"; // CT3 専用。root シェルを実行
    private static final String PARTED = "parted"; // CT3 でデバイスブロックの書き換えに必須
    private static final String PARTED_CMD = APP_PATH + PARTED + " -s " + MMCBLK0 + " "; // parted のコマンド短縮
    private static final String FRP = "frp";
    private static final String EXPDB = "expdb";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        echo("""
                ****************************
                   Welcome to Easy BLU ! :)
                     Easy BLU へようこそ！
                ****************************""");
        echo("fingerprint：" + Build.FINGERPRINT);
        init();
    }

    private void callFunc(Runnable func) {
        new Handler(Looper.getMainLooper()).postDelayed(func, DELAY_MS);
    }

    @NonNull
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
            error(e);
        }
        return stringBuilder;
    }

    /** @noinspection ResultOfMethodCallIgnored*/
    private void copyAssets(String file) {
        File bin = new File(getCacheDir(), file);
        try {
            InputStream inputStream = getAssets().open(file);
            FileOutputStream fileOutputStream = new FileOutputStream(bin, false);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) >= 0) fileOutputStream.write(buffer, 0, length);
            if (!file.equals(FRP)) bin.setExecutable(true); // chmod +x bin を省略
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

    private void notify(String str) {
        echo("- 通知：" + str);
    }

    private void warning(String str) {
        echo("- 警告：" + str);
    }

    private void error(Exception e) {
        echo("- エラー：" + System.lineSeparator() + e);
    }

    private void init() {
        TextView textView = findViewById(R.id.text_status);
        textView.setText("""
                ブートローダーアンロックに必要なシステム改ざん処理を実行しますか？
                続行するには [実行] を押下してください""");
        Button mainButton = findViewById(R.id.button_main);
        Button subButton = findViewById(R.id.button_sub);
        mainButton.setEnabled(true);
        mainButton.setText("実行");
        mainButton.setOnClickListener(v -> {
            mainButton.setEnabled(false);
            mainButton.setText(" ");
            subButton.setEnabled(false);
            subButton.setText(" ");
            textView.setText("""
                    デバイスには処理が終了するまで絶対に触れないでください。
                    
                    デバイスが再起動した場合は、再度実行してください。""");
            warning("""
                    デバイスには処理が終了するまで絶対に触れないでください。
                    
                    デバイスが再起動した場合は、再度実行してください。""");
            notify("エクスプロイトをコピーしています。");
            copyAssets(CT3 ? MTK_SU : SHRINKER);
            callFunc(this::setup);
        });
        subButton.setEnabled(true);
        subButton.setText("設定アプリを開く");
        subButton.setOnClickListener(v -> {
            try {
                Settings.System.putInt(getContentResolver(), DCHA_STATE, DIGICHALIZE_STATUS_DIGICHALIZED);
                startActivity(new Intent().setClassName(SETTINGS_PACKAGE, SETTINGS_ACTIVITY));
                finish();
            } catch (Exception e) {
                error(e);
            }
        });
    }

    private void setup() { // retry() と同様
        exec(APP_PATH + (CT3 ? MTK_SU : SHRINKER));
        notify((CT3 ? MTK_SU : SHRINKER) + " を実行しました");
        if (exec("getenforce").toString().contains(PERMISSIVE)) {
            notify("成功しました。");
            notify(CT3 ? EXPDB + " のサイズを計算します。" : FRP + " の修正を試みます。");
            callFunc(CT3 ? this::checkFixed : this::overwriteFrp);
        } else {
            warning("失敗しました。再度実行します。");
            callFunc(this::setup);
        }
    }

    private void parted(String cmd) {
        exec(PARTED_CMD + cmd);
    }

    private void overwriteFrp() {
        notify("DchaService にバインドしています。");
        if (!bindService(new Intent(DCHA_SERVICE).setPackage(DCHA_PACKAGE), new ServiceConnection() {

            /** @noinspection ResultOfMethodCallIgnored*/
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                IDchaService mDchaService = IDchaService.Stub.asInterface(iBinder);
                MainActivity.this.notify(FRP_ORIGIN_PATH + " を " + FRP_FIXING_PATH + " にコピーしています。");
                try {
                    mDchaService.copyUpdateImage(FRP_ORIGIN_PATH, DCHA_SYSTEM_COPY + FRP_FIXING_PATH);
                    mDchaService.copyUpdateImage(FRP_ORIGIN_PATH, "/cache/" + FRP); // オリジナルを保持
                } catch (Exception e) {
                    warning(FRP + " のコピーに失敗しました。");
                    error(e);
                    callFunc(MainActivity.this::init);
                }

                try {
                    File old = new File(FRP_FIXING_PATH); // ファイルサイズの計算に使用
                    File spy = new File(FRP_FIXED_PATH);
                    DataInputStream dataInStream = new DataInputStream(new BufferedInputStream(new FileInputStream(old)));
                    DataOutputStream dataOutStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(spy)));

                    int[] tmpHex = new int[(int) old.length()];
                    int i = 0;

                    while (true) {
                        int b = dataInStream.read();
                        if (b == -1) break;
                        tmpHex[i] = b;
                        i++;
                    }
                    tmpHex[tmpHex.length - 1] = 1;
                    for (int q : tmpHex) dataOutStream.write(q);

                    dataInStream.close();
                    old.delete(); // 一時的に参照されたファイルを削除
                    dataOutStream.close();

                    MainActivity.this.notify(FRP_FIXING_FILE + " を " + FRP_FIXED_FILE + "  として修正が完了しました。");
                    MainActivity.this.notify(FRP_FIXED_FILE + " を " + FRP_ORIGIN_PATH + " に上書きしています。");
                    // 修正した frp を、元の場所に上書き
                    mDchaService.copyUpdateImage(FRP_FIXED_PATH, DCHA_SYSTEM_COPY + FRP_ORIGIN_PATH);
                    // 上書き後に修正済みのファイルも削除
                    spy.delete();
                    callFunc(MainActivity.this::openSettings); // 設定アプリを起動
                } catch (Exception e) {
                    error(e);
                    callFunc(MainActivity.this::init);
                }
                unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                unbindService(this);
            }
        }, Context.BIND_AUTO_CREATE)) {
            warning("DchaService への接続に失敗しました。");
            callFunc(this::init);
        }
    }

    private void checkFixed() {
        if (getExpdbSize().contains("124MB   134MB")) { // 純正 expdb のセクタ範囲
            notify(EXPDB + " は修正されていません。");
        } else {
            notify(EXPDB + " は既に修正済みです。");
            notify("既存の " + FRP + " を削除します。");
            parted("rm 24");
        }
        callFunc(this::fixExpdb);
    }

    @NonNull
    private String getExpdbSize() {
        copyAssets(PARTED);
        notify("mmcblk0 の詳細を出力します。");
        return exec(PARTED_CMD + "print").toString();
    }

    private void fixExpdb() {
        notify(EXPDB + " を削除します。");
        parted("rm 13");
        notify(EXPDB + " を 9MB で再生成します。");
        parted("mkpart " + EXPDB + " 124MB 133MB");
        notify(EXPDB + " のラベルを設定します。");
        parted("name 13 " + EXPDB);
        notify(EXPDB + " のフラグを修正します。");
        parted("toggle 13 msftdata");
        callFunc(this::createFrp);
    }

    private void createFrp() {
        notify(FRP + " を 1MB で生成します。");
        parted("mkpart " + FRP + " 133MB 134MB");
        notify(FRP + " のラベルを設定します。");
        parted("name 24 " + FRP);
        notify(FRP + " のフラグを修正します。");
        parted("toggle 24 msftdata");
        notify(PART24 + " の所有者を system グループを shell に書き換えます。");
        exec("chown system:shell " + PART24);
        notify(PART24 + " を rw で再マウントします。");
        exec("mount -o remount,rw " + PART24);
        notify(PART24 + " に 読取と書込の権限を付与します。");
        exec("chmod o+rw "+ PART24);
        notify(PART24 + " を上書き修正します。");
        copyAssets(FRP);
        exec("dd if=" + APP_PATH + FRP + " of=" + PART24); // 必ずフルパス
        callFunc(this::doBootloader);
    }

    private void doBootloader() {
        notify("すべての修正が完了しました！");
        TextView textView = findViewById(R.id.text_status);
        textView.setText("bootloader へ再起動しますか？");
        Button mainButton = findViewById(R.id.button_main);
        Button subButton = findViewById(R.id.button_sub);
        mainButton.setEnabled(true);
        mainButton.setText("はい");
        mainButton.setOnClickListener(v -> exec("reboot bootloader"));
        subButton.setEnabled(true);
        subButton.setText("いいえ");
        subButton.setOnClickListener(v -> openSettings());
    }
    
    private void openSettings() {
        notify("すべての修正が完了しました！");
        TextView textView = findViewById(R.id.text_status);
        textView.setText("設定または開発者向けオプションを開きますか？");
        Button mainButton = findViewById(R.id.button_main);
        Button subButton = findViewById(R.id.button_sub);
        mainButton.setEnabled(true);
        mainButton.setText("開く");
        mainButton.setOnClickListener(v -> {
            try {
                Settings.System.putInt(getContentResolver(), DCHA_STATE, DIGICHALIZE_STATUS_DIGICHALIZED);
                startActivity(
                        Settings.Secure.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
                                ? new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) // 開発者向けオプションが解放されている場合は開く
                                : new Intent().setClassName(SETTINGS_PACKAGE, SETTINGS_ACTIVITY) // 未開放の場合は設定アプリを開く
                );
            } catch (Exception e) {
                error(e);
            }
        });
        subButton.setEnabled(true);
        subButton.setText("アプリを終了");
        subButton.setOnClickListener(v -> finish());
    }
}
