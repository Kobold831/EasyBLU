# Easy Bootloader Unlock

このアプリケーションは、ブートローダをアンロックすることができます。

## 注意事項

> [!CAUTION]
> このアプリケーションが起因した一切の問題を開発者は責任を負いません。

> [!WARNING]
> 実行中はデバイスに触れないでください。

デバイスが再起動してしまった場合は、エクスプロイトの発動に失敗しています。デバイスが起動したら再度実行してください。

最後まで処理が終了したら、adbで以下のコマンドを入力してbootloaderモードに入ってください。

adb reboot bootloader

次に、fastboot flashing unlockを実行して、音量のプラスボタンを長押しするとブートローダがアンロックされます。

## 対応機種

- チャレンジパッド Neo
- チャレンジパッド Next

## イメージ

<img src="images/image-01.png">

## 外部ライブラリー

このアプリは以下のライブラリーを使用しています。

- app/src/main/assets/**shrinker**  
  [**CVE-2022-38181**](https://github.com/SmileTabLabo/CVE-2022-38181)
