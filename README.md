# fuwa-hands (Android)

ユーザーのスマホの画面を AI エージェントが見て、タップ操作するための常駐アプリ。
PC 側の fuwa-hands エージェントと API の形を揃えてある。

## 仕組み
- **画面の読み取り**: アクセシビリティサービスで UI ツリー（テキスト・座標・押せるかどうか）を取得
- **スクリーンショット**: MediaProjection（開始のたびに本人の許可ダイアログが出る＝安全弁）
- **操作**: アクセシビリティのジェスチャーでタップ／スワイプ／長押し、戻る・ホーム等の全体操作、テキスト入力
- **API**: LAN 内 HTTP（ポート 8766、Bearer トークン必須）。前面サービスとして動作し、通知からいつでも停止できる

## API
| メソッド | パス | 内容 |
|---|---|---|
| GET | /health | 状態（アクセシビリティ・キャプチャ・画面サイズ） |
| GET | /screenshot | JPEG（X-Width / X-Height ヘッダ付き） |
| GET | /screen/tree | UI ツリー JSON |
| POST | /tap | {x, y} |
| POST | /swipe | {x1, y1, x2, y2, duration_ms?} |
| POST | /long_press | {x, y, duration_ms?} |
| POST | /key | {key: back/home/recents/notifications/quick_settings/power_dialog/lock} |
| POST | /input/type | {text}（フォーカス中の入力欄へ） |

## できないこと（Android の仕様）
- ロック画面・PIN 入力の操作
- 銀行アプリ等 FLAG_SECURE な画面の撮影・読み取り

## ビルド（手組み）
開発環境で Gradle のデーモンが安定しなかったため、配布 APK は `build-manual.sh` による手組みビルドで作っている。Gradle 構成ファイルも残してあるが、検証済みの手順はこちら。

1. JDK 21、Android SDK 34（platforms/android-34、build-tools/34.0.0）、Kotlin コンパイラ 2.0.21 を用意する
2. `build-manual.sh` 冒頭のパス（JAVA_HOME / SDK / kotlinc の場所）を自分の環境に合わせて書き換える
3. スクリプトを実行する。中身は標準工程をコマンドラインで直接回すもの:
   kotlinc でコンパイル → aapt2 compile/link で APK 化 → d8 で dex 化 → zipalign → apksigner で署名
4. 署名鍵 `debug.keystore` は初回実行時にプロジェクト直下へ自動生成される。**ビルドディレクトリの外に置くこと**（鍵が変わると署名が変わり、上書きインストールできなくなる）。鍵ファイル自体はこのリポジトリに含めていない
5. 成果物は `fuwa-hands-0.2.0.apk`（versionCode 2 / versionName 0.2.0、minSdk 29 / targetSdk 34）

## セットアップ（本人の操作）
1. APK をインストール
2. アプリを開き「1. アクセシビリティ設定を開く」→ fuwa-hands を有効化
3. 「2. 画面共有を許可して開始」→ システムの許可ダイアログで開始
4. 画面に表示される API トークンを、操作するエージェント側に一度だけ伝える
