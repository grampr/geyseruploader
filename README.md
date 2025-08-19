# GeyserUpdater

GeyserMC / Floodgate の自動更新プラグイン。Spigot(Paper 等), BungeeCord, Velocity をサポート。

機能
- GeyserMC と Floodgate の最新版（安定版）を Jenkins/ダウンロードAPI から取得し、plugins ディレクトリの既存JARを上書き
- 自動更新チェック
  - サーバー起動時
  - 指定間隔（例: 12時間）
  - 指定権限保有プレイヤーのログイン時（ON/OFF可）
- 手動コマンド: /geyserupdate（権限: geyserupdater.admin）
- メッセージ・対象・間隔・再起動コマンドの設定

ビルド
- 前提: Java 17, Maven 3.8+
- 手順:
  - git clone したフォルダで mvn package
  - 生成物:
    - spigot/target/GeyserUpdater-Spigot-1.0.0.jar
    - bungee/target/GeyserUpdater-Bungee-1.0.0.jar
    - velocity/target/GeyserUpdater-Velocity-1.0.0.jar

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
- checkOnStartup: 起動時チェックの有効/無効
- periodic.enabled: 定期チェックの有効/無効
- periodic.intervalHours: チェック間隔（時間）
- adminLogin.enabled: 権限保持者ログイン時チェックの有効/無効
- adminLogin.permission: トリガーとなる権限（デフォルト: geyserupdater.admin）
- targets.geyser | targets.floodgate: 更新対象の選択
- postUpdate.notifyConsole: コンソールへ通知
- postUpdate.notifyPlayersWithPermission: 権限保持者にチャットで通知
- postUpdate.runRestartCommand: 更新後に自動で再起動コマンドを実行
- postUpdate.restartCommand: 実行する再起動コマンド（例: restart / end）
- messages.*: 送信メッセージのカスタマイズ

動作仕様
- ダウンロードURL:
  - Geyser: https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/{platform}
  - Floodgate: https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/{platform}
  - {platform} は spigot | bungeecord | velocity
- 一時ファイルに最新JARをダウンロードし、既存JARとSHA-256で比較
  - 同一なら上書きせず「最新」と判定
  - 異なれば原子置換で上書き
- 上書き後はサーバー/プロキシの再起動が必要です
  - 自動再起動を有効にする場合は postUpdate.runRestartCommand を true にし、restartCommand を環境に合わせて設定してください

注意/既知の制限
- ネットワーク障害などによりダウンロードが失敗する場合、既存ファイルには影響しません
- Geyser/Floodgate のファイル名が特殊で plugins 直下以外にある場合は検出できません（plugins直下の *.jar 探索が前提）
- バージョン番号の表示は行っていません（ハッシュ比較による更新判定）

ライセンス
- ご希望があれば追って設定可能です（MITなど）

