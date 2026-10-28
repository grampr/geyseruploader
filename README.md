# GeyserUpdater

GeyserMC / Floodgate / ViaVersion / ViaBackwards / ViaRewind の自動更新プラグイン（1.2.0）。Spigot(Paper 等), BungeeCord, Velocity をサポート。

機能
- GeyserMC と Floodgate の最新版（安定版）を Jenkins/ダウンロードAPI から取得し、plugins ディレクトリの既存JARを上書き
- ViaVersion / ViaBackwards / ViaRewind の実行環境に対応する安定版を個別に自動更新（任意）
- MCXboxBroadcast の最新版（Modrinth）を取得し、Geyser の extensions フォルダへ配置（任意）
- 自動更新チェック
  - サーバー起動時
  - 指定間隔（例: 12時間）
  - 指定権限保有プレイヤーのログイン時（ON/OFF可）
- 手動コマンド: /geyserupdate（権限: geyserupdater.admin）
- 言語・メッセージ・対象・間隔・再起動コマンドの設定

ビルド
- 前提: Java 17, Maven 3.8+
- 手順:
  - git clone したフォルダで mvn package
  - 生成物:
    - spigot/target/geyserupdater-spigot.jar
    - bungee/target/geyserupdater-bungee.jar
    - velocity/target/geyserupdater-velocity.jar

導入
- 対応サーバーに該当する JAR を plugins フォルダへ配置
- 起動すると config.yml が生成されます（このREADME同梱の初期値と同等）
- 既存の Geyser / Floodgate JAR は同フォルダ配下でファイル名に geyser/floodgate を含むものが自動検出されます
  - 見つからない場合は標準名で新規作成（例: Geyser-Spigot.jar, floodgate-velocity.jar）

コマンド・権限
- /geyserupdate
  - 説明: 直ちに更新チェックを実行
  - 権限: geyserupdater.admin

設定ファイル（config.yml）
- enabled: プラグイン有効/無効
- language: 言語設定（ja / en / de / ko、デフォルト: ja）
- checkOnStartup: 起動時チェックの有効/無効
- periodic.enabled: 定期チェックの有効/無効
- periodic.intervalHours: チェック間隔（時間）
- adminLogin.enabled: 権限保持者ログイン時チェックの有効/無効
- adminLogin.permission: トリガーとなる権限（デフォルト: geyserupdater.admin）
- targets.geyser | targets.floodgate | targets.mcxboxbroadcast: 更新対象の選択
- targets.viaversion | targets.viabackwards | targets.viarewind: Via系を個別に更新（既定値: false）
- postUpdate.notifyConsole: コンソールへ通知
- postUpdate.notifyPlayersWithPermission: 権限保持者にチャットで通知
- postUpdate.runRestartCommand: 更新後に自動で再起動コマンドを実行
- postUpdate.restartCommand: 実行する再起動コマンド（例: restart / end）
- postUpdate.restartDelaySeconds: 更新完了から再起動コマンド実行までの待機秒数（既定値: 0）
- messages.<lang>.*: 送信メッセージのカスタマイズ（例: messages.ja.*）
  - 互換のため、language=ja の場合は従来の messages.* でも読み込み可

動作仕様
- ダウンロードURL:
  - Geyser: https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/{platform}
  - Floodgate: https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/{platform}
  - MCXboxBroadcast: https://api.modrinth.com/v2/project/mcxboxbroadcast/version
  - {platform} は spigot | bungeecord | velocity
- 一時ファイルに最新JARをダウンロードし、既存JARとSHA-256で比較
  - 同一なら上書きせず「最新」と判定
  - 異なれば原子置換で上書き
- 上書き後はサーバー/プロキシの再起動が必要です
  - 自動再起動を有効にする場合は postUpdate.runRestartCommand を true にし、restartCommand と restartDelaySeconds を環境に合わせて設定してください
 - MCXboxBroadcast は Geyser の extensions フォルダに配置します
   - 例: plugins/Geyser-Spigot/extensions

注意/既知の制限
- ネットワーク障害などによりダウンロードが失敗する場合、既存ファイルには影響しません
- Geyser/Floodgate のファイル名が特殊で plugins 直下以外にある場合は検出できません（plugins直下の *.jar 探索が前提）
- MCXboxBroadcast はプラットフォーム別の Geyser extensions フォルダに配置されます
- バージョン番号の表示は行っていません（ハッシュ比較による更新判定）

ライセンス
- Apache License 2.0

### Via系の自動更新（1.2.0）

既存の `config.yml` の `targets:` 内に必要な項目を追加してください。
項目がない場合は無効のままで、既存設定は書き換えません。

```yaml
targets:
  geyser: true
  floodgate: true
  mcxboxbroadcast: false
  viaversion: true
  viabackwards: true
  viarewind: false
```

設定変更後はサーバー/プロキシを再起動してください。起動時・定期・管理者ログイン時・
`/geyserupdate` はすべてこの対象設定を使います。コマンドと権限の追加はありません。
ViaBackwards/ViaRewindを使う場合は依存するViaVersionも導入し、更新対象に含めてください。
各プラグインの現行版が対応するサーバー/Java環境で使用してください。
現行の公式Via JARはBungeeCord用とは異なるため、BungeeCordではVia系をスキップし、既存JARを維持します。
ViaRewindを使う場合はViaBackwardsも必要です。

取得元はViaVersion公式のHangar配布です。実際のPaper/SpigotのMinecraftバージョン
（例: `1.21` と `1.21.1` は区別）またはVelocityバージョンを自動検出し、
Hangarの対応プラットフォーム情報に明記されたReleaseチャンネルの候補から最新版を選びます。
Snapshot、対応情報がない版、別のパッチバージョン専用の版は選びません。
対応候補がない場合、環境のバージョンを判別できない場合、通信に失敗した場合は既存JARを維持します。
ダウンロード後は実行中のJavaで読み込めるクラスバージョンと対象プラットフォームの記述ファイルを検証し、
Java要件を満たさなければ更新を中止します（古いJava向けの別候補を自動探索する機能はありません）。
導入済みViaより古い版へのダウングレードも行いません。
既存の `ViaVersion-<version>.jar` 等は同じファイル名で置換し、見つからない場合は
`ViaVersion.jar` / `ViaBackwards.jar` / `ViaRewind.jar` を作成します。
上流SHA-256がある場合は検証し、同一ならダウンロードを省略します。
Paper/SpigotでJARがロックされている場合は `plugins/update` に保存します。
更新の反映には再起動が必要です。Hangar APIの制限や通信失敗時は既存JARを維持します。
