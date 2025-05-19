# Easy Bootloader Unlock

チャレンジパッドのブートローダーをアンロックします。

## 利用方法

1. \[**こう新中です。**\]となるまで、[**SetupLogin**](https://github.com/Kobold831/SetupLogin/blob/master/docs/README.md) の説明ある通りに進めてください。
2. **EasyBLU** をインストールします
3. **EasyBLU** が起動します

## 注意事項

> [!CAUTION]
> このアプリケーションが起因した一切の問題を開発者は責任を負いません。

> [!WARNING]
> 実行中はデバイスに触れないでください。

エクスプロイトの発動に失敗してデバイスが再起動した場合は、ホームとして **EasyBLU** を選択してください。

## ブートローダーアンロック

次の記事を参考にしてください：  
[**チャレンジパッド３/Neo/Next で Playストアを使おう！（改訂版）**](https://zenn.dev/s1204it/articles/efd006bf3f5736)

## 対応機種

- チャレンジパッド３ (CT3 / TAB-A04-BR3)
- チャレンジパッド Neo (CTX / TAB-A05-BD)
- チャレンジパッド Next (CTZ / TAB-A05-BA1)

## サンプル画像

[![](https://github.com/user-attachments/assets/074a1f80-3a87-4bd1-ba5a-48a7ee1ab567)]()

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
