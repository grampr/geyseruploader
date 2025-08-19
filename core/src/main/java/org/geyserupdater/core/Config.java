package org.geyserupdater.core;

import java.util.List;
import java.util.Map;

public class Config {
    public boolean enabled = true;

    public boolean checkOnStartup = true;

    public Periodic periodic = new Periodic();
    public static class Periodic {
        public boolean enabled = true;
        public int intervalHours = 12;
    }

    public AdminLogin adminLogin = new AdminLogin();
    public static class AdminLogin {
        public boolean enabled = true;
        public String permission = "geyserupdater.admin";
    }

    public Targets targets = new Targets();
    public static class Targets {
        public boolean geyser = true;
        public boolean floodgate = true;
    }

    public PostUpdate postUpdate = new PostUpdate();
    public static class PostUpdate {
        public boolean notifyConsole = true;
        public boolean notifyPlayersWithPermission = true;
        public boolean runRestartCommand = false;
        public String restartCommand = "restart";
    }

    public Messages messages = new Messages();
    public static class Messages {
        public String prefix = "§a[GeyserUpdater]§r ";
        public String checking = "更新を確認しています...";
        public String upToDate = "{project} は最新です。";
        public String updated = "{project} を最新ビルドに更新しました。";
        public String noTarget = "更新対象のプロジェクトが有効化されていません。";
        public String failed = "{project} の更新に失敗しました: {error}";
        public String promptRestart = "更新が適用されました。サーバーの再起動が必要です。";
        public String startUpCheck = "起動時の自動更新チェックを開始します。";
        public String periodicCheck = "定期更新チェックをスケジュールしました（{hours}時間ごと）。";
        public String adminLoginCheck = "権限保持者のログインを検知し、更新チェックを実行します。";
        public String manualTriggered = "手動の更新チェックを開始します。";
        public String nothingToDo = "有効な対象がありません。config.yml をご確認ください。";
        public String done = "更新チェックが完了しました。";
    }
}