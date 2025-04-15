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

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import jp.co.benesse.dcha.dchaservice.IDchaService;

//import jp.co.benesse.touch.setuplogin.R;

/**
 * EasyBLU
 * @author Kobold
 * @noinspection ResultOfMethodCallIgnored
 */
public class MainActivity extends Activity {

    private static final String APP_PATH = "/data/data/com.saradabar.easyblu/cache/"; // getCacheDir() + "/" と同様
    private static final String BLOCK_DEVICE = "/dev/block/platform/bootdevice/";
    private static final String BOOTDEVICE = BLOCK_DEVICE + "mmcblk0"; // eMMC
    private static final String PART24 = BOOTDEVICE + "p24"; // CT3 で新規パーティションを作成した際の割振番号
    private static final String FRP = "frp";
    private static final String EXPDB = "expdb";
    private static final String FRP_BLOCK = BLOCK_DEVICE + "by-name/" + FRP; // ro.frp.pst と同様
    private static final String FRP_COPY = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + FRP; // /sdcard/Download/frp に抽出

    private static final String GETENFORCE = "getenforce"; // SELinux ポリシー強制状態の確認
    private static final String PERMISSIVE = "Permissive"; // デバイスを書き換えられるかどうかの確認
    private static final String SHRINKER = "shrinker"; // 純正 boot の CTX/CTZ 専用。`getenforce` を実行
    private static final String MTK_SU = "mtk-su"; // CT3 専用。root シェルを実行
    private static final String PARTED = "parted"; // CT3 でデバイスブロックの書き換えに必須
    private static final String PARTED_CMD = APP_PATH + PARTED + " -s " + BOOTDEVICE + " "; // parted のコマンド短縮

    private static final String MODEL_CT3 = "TAB-A03-BR3"; // CT3
    private static final String MODEL_CTX = "TAB-A05-BD"; // CTX
    private static final String MODEL_CTZ = "TAB-A05-BA1"; // CTZ
    private static final boolean CT3 = Build.MODEL.equals(MODEL_CT3); // CT3 かどうかの真偽値
    private static final boolean CTX = Build.MODEL.equals(MODEL_CTX); // CTX で同上
    private static final boolean CTZ = Build.MODEL.equals(MODEL_CTZ); // CTZ で同上

    private static final String DCHA_PACKAGE = "jp.co.benesse.dcha.dchaservice"; // DchaService を使用
    private static final String DCHA_SERVICE = DCHA_PACKAGE + ".DchaService"; // copyUpdateImage を使ってシステム権限でファイルを操作
    private static final int DIGICHALIZE_STATUS_DIGICHALIZED = 3; // 開発者向けオプションのロック(BenesseExtension.checkPassword)の阻止
    private static final String DCHA_SYSTEM_COPY = "/cache/.."; // 内部の if 文で弾かれるのを防ぐ
    private IDchaService mDchaService = null;
    private static final Intent BIND_DCHA = new Intent(DCHA_SERVICE).setPackage(DCHA_PACKAGE);
    private final ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mDchaService = IDchaService.Stub.asInterface(iBinder);
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mDchaService = null;
        }
    };
    private static final File COUNT_DCHA_COMPLETED_FILE = new File("/factory/count_dcha_completed");

    /**
     * @param savedInstanceState If the activity is being re-initialized after
     *     previously being shut down then this Bundle contains the data it most
     *     recently supplied in {@link #onSaveInstanceState}.  <b><i>Note: Otherwise it is null.</i></b>
     * @see #echo(String)
     * @see #init()
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        echo("""
                ****************************
                   Welcome to Easy BLU ! :)
                     Easy BLU へようこそ！
                ****************************""");
        echo("fingerprint：" + Build.FINGERPRINT);
        init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            hideNavigationBar(false);
        } catch (Exception e) {
            error(e);
        }
    }

    /**
     * APK の <b>assets</b> 内のファイルを cache にコピー。実行権限も付与。
     * ただし、{@code frp} のみ、コピー先が {@code /sdcard/Download} である
     * @param file <b>assets</b> のファイル名
     * @author Kobold
     * @since v1.0
     */
    private void copyAssets(@NonNull String file) {
        File bin = new File(file.equals(FRP) ? FRP_COPY : APP_PATH + file);
        try {
            InputStream inputStream = getAssets().open(file);
            FileOutputStream fileOutputStream = new FileOutputStream(bin, false);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) >= 0) fileOutputStream.write(buffer, 0, length);
            if (!file.equals(FRP)) bin.setExecutable(true); // chmod +x bin を省略
            fileOutputStream.close();
            inputStream.close();
        } catch (Exception e) {
            error(e);
        }
    }

    /**
     * 関数の呼出しに使用
     * @param func Runnable 形式
     * @author Syuugo
     * @since v3.0
     */
    private void callFunc(Runnable func) {
        new Handler(getMainLooper()).post(func);
    }

    /**
     * コマンドの実行に使用。
     * CT3 の場合は、常に {@code mtk-su} を実行
     * @param cmd 実行コマンド
     * @return 必要な場合は文字列を取得
     * @throws RuntimeException ランタイムスロー
     * @see #parted(String)
     * @author Kobold
     * @since v3.0
     */
    @NonNull
    private StringBuilder exec(String cmd) throws RuntimeException {
        Process process;
        BufferedWriter bufferedWriter;
        BufferedReader bufferedReader;
        StringBuilder stringBuilder = new StringBuilder();

        try {
            process = Runtime.getRuntime().exec(CT3 ? APP_PATH + MTK_SU + " -c " + cmd : cmd + System.lineSeparator());
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            bufferedWriter.write("exit" + System.lineSeparator());
            bufferedWriter.flush();
            process.waitFor();

            String data;
            while ((data = bufferedReader.readLine()) != null) stringBuilder.append(data).append(System.lineSeparator());
            bufferedReader.close();
            bufferedWriter.close();
            process.destroy();
            echo(stringBuilder.toString());
        } catch (Exception e) {
            error(e);
        }
        return stringBuilder;
    }

    /**
     * 文字列をコンソール上に出力
     * @param str 出力する文字列
     * @see #notify(String)
     * @see #warning(String)
     * @see #error(Exception)
     * @author Kobold
     * @since v3.0
     */
    private void echo(String str) {
        TextView textView = findViewById(R.id.text_console);
        textView.append(str + System.lineSeparator());
        ScrollView scrollView = findViewById(R.id.scroll_console);
        scrollView.fullScroll(View.FOCUS_DOWN);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    /**
     * 常に {@code - 通知：} を付けて出力
     * @param str 出力したい文字列
     * @see #echo(String)
     * @author Syuugo
     * @since v3.0
     */
    private void notify(String str) {
        echo("- 通知：" + str);
    }

    /**
     * 常に {@code - 警告：} を付けて出力
     * @param str 出力したい文字列
     * @see #echo(String)
     * @author Syuugo
     * @since v3.0
     */
    private void warning(String str) {
        echo("- 警告：" + str);
    }

    /**
     * 常に {@code - エラー：} を付けて出力。
     * エラー内容も同時に出力する。
     * 処理を最初から実行し直す
     * @param e Exception
     * @see #echo(String)
     * @see #init()
     * @author Syuugo
     * @since v3.0
     */
    private void error(Exception e) {
        echo("- エラー：" + System.lineSeparator() + e);
        TextView textView = findViewById(R.id.text_status);
        textView.setText("始めからやり直しますか？");
        Button mainButton = findViewById(R.id.button_main);
        Button subButton = findViewById(R.id.button_sub);
        mainButton.setEnabled(true);
        mainButton.setText("はい");
        mainButton.setOnClickListener(v -> callFunc(this::init));
        subButton.setEnabled(true);
        subButton.setText("いいえ");
        subButton.setOnClickListener(v -> finish());
    }


    /**
     * {@code - エラー：} を付けて出力。
     * 処理を停止しアプリを終了させる
     * @param str 出力したい文字列
     * @see #echo(String)
     * @author Syuugo
     * @since v3.2
     */
    private void stop(String str) {
        echo("- エラー：" + System.lineSeparator() + str);
        TextView textView = findViewById(R.id.text_status);
        textView.setText("アプリを終了してください。");
        Button mainButton = findViewById(R.id.button_main);
        Button subButton = findViewById(R.id.button_sub);
        mainButton.setEnabled(false);
        mainButton.setText(" ");
        mainButton.setOnClickListener(view -> {});
        subButton.setEnabled(true);
        subButton.setText("アプリを終了");
        subButton.setOnClickListener(v -> finish());
    }

    /**
     * 初期実行関数。
     * 通常はそのまま {@link #setup()} を実行。
     * エラーがある度に呼び出される
     * @see #setup()
     * @see #error(Exception)
     * @author Kobold
     * @since v1.0
     */
    private void init() {
        if (!CT3 && !CTX && !CTZ) {
            stop("対象端末ではありません");
        } else if (!bindService(BIND_DCHA, mConn, Context.BIND_AUTO_CREATE)) {
            stop("DchaService に接続できませんでした");
        } else {
            hideNavigationBar(true);
            TextView textView = findViewById(R.id.text_status);
            textView.setText("""
                ブートローダーアンロックに必要なシステム改ざん処理を実行しますか？
                この処理を実行したことによる損害等について開発者は一切の責任を取りません。
                
                続行するには [実行] を押下してください""");
            Button mainButton = findViewById(R.id.button_main);
            Button subButton = findViewById(R.id.button_sub);
            mainButton.setEnabled(true);
            mainButton.setText("実行");
            mainButton.setOnClickListener(v -> {
                hideNavigationBar(true);
                mainButton.setEnabled(false);
                mainButton.setText(" ");
                subButton.setEnabled(false);
                subButton.setText(" ");
                textView.setText("""
                    デバイスには処理が終了するまで絶対に触れないでください。
                    
                    デバイスが再起動した場合は、再度実行してください。""");
                notify("エクスプロイトをコピーしています。");
                copyAssets(CT3 ? MTK_SU : SHRINKER);
                callFunc(this::setup);
            });
            subButton.setEnabled(true);
            subButton.setText("設定アプリを開く");
            subButton.setOnClickListener(v -> {
                setDchaStateCompleted();
                hideNavigationBar(false);
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                finish();
            });
        }
    }

    /**
     * エクスプロイト実行関数。SEStatus が Permissive かどうかで処理を分岐する。
     * 実行に失敗した場合は<b>自己を再度呼び出す</b>
     * @see #getBlockDeviceSize()
     * @see #overwriteFrp()
     * @author Syuugo
     * @since v3.0
     */
    private void setup() { // retry() と同様
        if (!CT3) {
            exec(APP_PATH + SHRINKER);
            notify(SHRINKER + " を実行しました");
        }
        if (exec(GETENFORCE).toString().contains(PERMISSIVE)) {
            notify("SELinux ポリシーの強制を解除しました。");
            notify(CT3 ? EXPDB + " のサイズを計算します。" : FRP + " の修正を試みます。");
            callFunc(CT3 ? this::checkFixed : this::overwriteFrp);
        } else {
            warning("失敗しました。再試行します。");
            callFunc(this::setup);
        }
    }

    /**
     * <b>DchaService</b> の <b>{@code copyUpdateImage}</b> を実行。
     * システム権限でファイルの操作が可能
     * @param src コピー元ファイルパス
     * @param dst コピー先ファイルパス。{@code /cache} から始まる必要があるが相対パス使用可能なので(ry
     * @see #overwriteFrp()
     * @author Syuugo
     * @since v3.0
     */
    private void copyFile(String src, String dst) {
        try {
            MainActivity.this.notify(src + " を " + dst + " にコピーしています。");
            if (mDchaService.copyUpdateImage(src, DCHA_SYSTEM_COPY + dst)) {
                MainActivity.this.notify(src + " を削除しています。");
                new File(src).delete();
            }
        } catch (Exception e) {
            error(e);
        }
    }

    /**
     * <b>DchaService</b> の <b>{@code hideNavigationBar}</b> を実行。
     * ナビゲーションバーを非表示にするかどうか
     * @param hide 非表示に巣つかどうか
     * @author Syuugo
     * @since v3.2
     */
    private void hideNavigationBar(boolean hide) {
        try {
            mDchaService.hideNavigationBar(hide);
        } catch (Exception e) {
            error(e);
        }
    }

    /**
     * BenesseExtension の保護状況に基づき DchaState を変更
     * @author Syuugo
     * @since v3.2
     */
    private void setDchaStateCompleted() {
        try {
            if (COUNT_DCHA_COMPLETED_FILE.exists()) {
                mDchaService.setSetupStatus(DIGICHALIZE_STATUS_DIGICHALIZED);
            }
        } catch (Exception e) {
            error(e);
        }
    }

    /**
     * CTX/CTZ にて、FRP を上書き
     * @see #copyAssets(String)
     * @see #copyFile(String, String)
     * @author Kobold
     * @since v1.0
     */
    private void overwriteFrp() {
        copyAssets(FRP);
        try {
            notify(FRP + " を書き換えます。");
            copyFile(FRP_COPY, FRP_BLOCK); // 修正済み FRP を適用
        } catch (Exception e) {
            error(e);
        }
        callFunc(this::openSettings);
    }

    /**
     * parted のコマンドを実行
     * @param cmd parted のコマンド
     * @see #exec(String)
     * @see #getBlockDeviceSize()
     * @see #fixExpdb()
     * @see #createFrp()
     * @author Syuugo
     * @since v3.0
     */
    private void parted(String cmd) {
        exec(PARTED_CMD + cmd);
    }

    /**
     * mmcblk0 のパーティションの詳細を確認する関数。
     * {@code parted} のコピーも行う
     * @return パーティションの詳細
     * @see #checkFixed()
     * @author Syuugo
     * @since v3.0
     */
    @NonNull
    private String getBlockDeviceSize() {
        copyAssets(PARTED);
        notify("BOOTDEVICE の詳細を出力します。");
        return exec(PARTED_CMD + "print").toString();
    }

    /**
     * CT3 において、expdb が修正されているかを確認する関数
     * @see #getBlockDeviceSize()
     * @see #fixExpdb()
     * @see #createFrp()
     * @author Syuugo
     * @since v2.0
     */
    private void checkFixed() {
        notify("BenesseExtension による保護を回避します。");
        exec("touch /factory/ignore_dcha_completed");
        if (getBlockDeviceSize().contains("124MB   134MB")) { // 純正 expdb のセクタ範囲
            notify(EXPDB + " は修正されていません。");
        } else {
            notify(EXPDB + " は既に修正済みです。");
            notify("既存の " + FRP + " を削除します。");
            parted("rm 24");
        }
        callFunc(this::fixExpdb);
    }

    /**
     * expdb の修正を行う関数
     * @see #checkFixed()
     * @see #createFrp()
     * @author Syuugo
     * @since v2.0
     */
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

    /**
     * CT3 の FRP を作成する関数。
     * パーティション間に作成するため、expdb の修正が必須
     * @see #fixExpdb()
     * @author Syuugo
     * @since v2.0
     */
    private void createFrp() {
        notify(FRP + " を 1MB で生成します。");
        parted("mkpart " + FRP + " 133MB 134MB");
        notify(FRP + " のラベルを設定します。");
        parted("name 24 " + FRP);
        notify(FRP + " のフラグを修正します。");
        parted("toggle 24 msftdata");
        notify(PART24 + " を上書き修正します。");
        copyAssets(FRP);
        exec("dd if=" + FRP_COPY + " of=" + PART24); // 必ずフルパス
        callFunc(this::openSettings);
    }

    /**
     * 設定アプリまたは開発者向けオプションを開く関数
     * @author Kobold
     * @since v2.0
     */
    private void openSettings() {
        hideNavigationBar(false);
        notify("すべての修正が完了しました！");
        TextView textView = findViewById(R.id.text_status);
        textView.setText("設定 または 開発者向けオプション を開きますか？");
        Button mainButton = findViewById(R.id.button_main);
        Button subButton = findViewById(R.id.button_sub);
        mainButton.setEnabled(true);
        mainButton.setText("開く");
        mainButton.setOnClickListener(v -> {
            setDchaStateCompleted();
            startActivity(new Intent(
                    Settings.Secure.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
                            ? Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS // 開発者向けオプションが解放されている場合は開く
                            : Settings.ACTION_SETTINGS // 未開放の場合は設定アプリを開く
            ));
        });
        subButton.setEnabled(true);
        subButton.setText("アプリを終了");
        subButton.setOnClickListener(v -> finish());
    }
}
