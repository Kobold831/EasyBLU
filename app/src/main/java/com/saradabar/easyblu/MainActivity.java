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
import android.os.RemoteException;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
        addText("");
        addText("続行するには、該当する数字を入力してください");
        addText("- 1：shrinker を実行し、Permissive に成れたら frp.bin を修正します。");
        addText("- 0：終了");
        addText("****************************");
        addText("fingerprint：" + Build.FINGERPRINT);
        init();
    }

    void init() {
        EditText editText = findViewById(R.id.edit);
        editText.setEnabled(true);
        Button button = findViewById(R.id.button);
        button.setEnabled(true);
        button.setOnClickListener(v -> {
            switch (editText.getText().toString()) {
                case "1":
                    confirm();
                    break;
                case "0":
                    finishAffinity();
                    break;
                default:
                    addText("- 通知：" + "コマンド [" + editText.getText().toString() + "] は実行できません");
                    break;
            }
            editText.getEditableText().clear();
        });
    }

    @Deprecated
    void confirm() {
        addText("- 確認：よろしいですか？続行するには 0 を入力してください");
        Button button = findViewById(R.id.button);
        button.setOnClickListener(v -> {
            EditText editText = findViewById(R.id.edit);
            if (editText.getText().toString().equals("0")) {
                editText.setEnabled(false);
                button.setEnabled(false);
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
            } else {
                addText("- 通知：この操作はキャンセルされました");
                init();
            }
            editText.getEditableText().clear();
        });
    }

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

    @Deprecated
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
                    addText("- 通知：コンピュータから bootloader モードを起動してブートローダをアンロックしてください。");
                } catch (IOException | RemoteException e) {
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
