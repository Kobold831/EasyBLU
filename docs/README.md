# Easy Bootloader Unlock

このアプリケーションは、チャレンジパッドのブートローダーをアンロックします。

## 利用方法

セットアップウィザードから簡単に利用できるようになりました。

> [!IMPORTANT]
> ダウンロードする `test_environment_info.xml` は、次のものを使用してください：
> <https://github.com/Kobold831/EasyBLU/releases/download/open/test_environment_info.xml>

1. \[**こう新中です。**\]となるまで、[**SetupLogin**](https://github.com/Kobold831/SetupLogin/blob/master/docs/README.md) の説明ある通りに進めてください。
2. **EasyBLU** が起動します！

## 注意事項

> [!CAUTION]
> このアプリケーションが起因した一切の問題を開発者は責任を負いません。

> [!WARNING]
> 実行中はデバイスに触れないでください。

エクスプロイトの発動に失敗してデバイスが再起動した場合は、ホームとして **EasyBLU** を選択してください。

最後まで処理が終了したら、開発者向けオプションから USB デバッグを有効にした後、ADB から以下のコマンドを実行して **bootloader** モードに入ってください。
```
adb reboot bootloader
```
次のコマンドを実行して、音量のプラスボタンを押すと、５秒程度でブートローダーがアンロックされます。
```
fastboot flashing unlock
```
完了次第、`factory` や `lk`, `boot` を書き換えてください。

<details><summary>書き換えるパーティションについて</summary>

### factory
- `count_dcha_completed` を削除
- `ignore_dcha_completed` を作成
- `dcha_hash` を作成  
  中身：`echo -n | sha256sum | cut -c-64`：`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`  
  これはパスワードが空の状態

### lk
- **Orange State** による５秒間の起動遅延処理のスキップ

### boot
- **Magisk** の埋め込み

</details>

## 対応機種

- チャレンジパッド３ (CT3 / TAB-A04-BR3)
- チャレンジパッド Neo (CTX / TAB-A05-BD)
- チャレンジパッド Next (CTZ / TAB-A05-BA1)

## サンプル画像

[![](https://github.com/user-attachments/assets/e7b4b17b-dab3-4d6b-a230-1157ea54f0db)](#)

## 問題の報告

不具合が発生している場合、または提案等がある場合は [Google フォームから報告](https://forms.gle/c1Jj52NN1uuduW4N9) できます。

## 外部ライブラリー

このアプリは以下のライブラリーを使用しています。

- **mali_shrinker_mmap32** (`shrinker`)  
  [SmileTabLabo/CVE-2022-38181: CVE-2022-38181 PoC for CTX(TAB-A05-BD) and CTZ(TAB-A05-BA1)](https://github.com/SmileTabLabo/CVE-2022-38181)
- **mtk-su**  
  [Amazing Temp Root for MediaTek ARMv8 \[2020-08-24\] | XDA Forums](https://xdaforums.com/t/3922213/)
- **parted**  
  [\[HOW TO\] BOOT FROM SD CARD \[SUCCESSFULLY\] on QMobile Z8 with BRICKED/DEAD eMMC | XDA Forums](https://xdaforums.com/t/3712171/)
