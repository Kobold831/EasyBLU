# Easy Bootloader Unlock

このアプリケーションは、チャレンジパッド Neo/Next のブートローダをアンロックすることができます。

## 利用方法

[SetupLogin](https://github.com/Kobold831/SetupLogin)から簡単に利用できるようになりました。

+ [SetupLogin](https://github.com/Kobold831/SetupLogin/blob/master/docs/README.md)にあるとおりにセットアップをしてください。
+ EasyBLUを選択して続行します。
+ このアプリが起動します。

エクスプロイトの発動に失敗してデバイスが再起動した場合は、ホームとしてEasyBLUを選択すると、起動時にこのアプリが起動します。

## 注意事項

> [!CAUTION]
> このアプリケーションが起因した一切の問題を開発者は責任を負いません。

> [!WARNING]
> 実行中はデバイスに触れないでください。

デバイスが再起動してしまった場合は、エクスプロイトの発動に失敗しています。アプリのバグではなく仕様ですので、デバイスが起動したら再度実行してください。

最後まで処理が終了したら、ADB から以下のコマンドを実行して **bootloader** モードに入ってください。
```
adb reboot bootloader
```
次のコマンドを実行して、音量のプラスボタンを押すと、５秒程度でブートローダがアンロックされます。
```
fastboot flashing unlock
```

## 対応機種

- チャレンジパッド３
- チャレンジパッド Neo
- チャレンジパッド Next

## サンプル画像

[![](images/image-01.png)](#)

## 問題の報告

予期せぬ動作、クラッシュ、不具合などが発生している場合、または新機能、改善、提案などがある場合は [Google フォームから報告](https://forms.gle/c1Jj52NN1uuduW4N9) できます。

## 外部ライブラリー

このアプリは以下のライブラリーを使用しています。

- **shrinker**  
  [SmileTabLabo/CVE-2022-38181: CVE-2022-38181 PoC for CTX(TAB-A05-BD) and CTZ(TAB-A05-BA1)](https://github.com/SmileTabLabo/CVE-2022-38181)
- **mtk-su**  
  [Amazing Temp Root for MediaTek ARMv8 \[2020-08-24\] | XDA Forums](https://xdaforums.com/t/3922213/)
- **parted**  
  [\[HOW TO\] BOOT FROM SD CARD \[SUCCESSFULLY\] on QMobile Z8 with BRICKED/DEAD eMMC | XDA Forums](https://xdaforums.com/t/3712171/)