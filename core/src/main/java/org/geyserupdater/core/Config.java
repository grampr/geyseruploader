package org.geyserupdater.core;

import java.util.Locale;

public class Config {
    public boolean enabled = true;

    public String language = "ja";

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
        public boolean mcxboxbroadcast = false;
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
        public String noPermission = "権限がありません。";

        public static Messages english() {
            Messages m = new Messages();
            m.prefix = "§a[GeyserUpdater]§r ";
            m.checking = "Checking for updates...";
            m.upToDate = "{project} is up to date.";
            m.updated = "Updated {project} to the latest build.";
            m.noTarget = "No update targets are enabled.";
            m.failed = "Failed to update {project}: {error}";
            m.promptRestart = "Update applied. Server restart is required.";
            m.startUpCheck = "Starting update check on startup.";
            m.periodicCheck = "Scheduled periodic update checks (every {hours} hours).";
            m.adminLoginCheck = "Detected admin login; running update check.";
            m.manualTriggered = "Starting manual update check.";
            m.nothingToDo = "No valid targets. Please check config.yml.";
            m.done = "Update check completed.";
            m.noPermission = "You do not have permission.";
            return m;
        }

        public static Messages german() {
            Messages m = new Messages();
            m.prefix = "§a[GeyserUpdater]§r ";
            m.checking = "Suche nach Updates...";
            m.upToDate = "{project} ist auf dem neuesten Stand.";
            m.updated = "{project} auf die neueste Version aktualisiert.";
            m.noTarget = "Keine Update-Ziele sind aktiviert.";
            m.failed = "Aktualisierung von {project} fehlgeschlagen: {error}";
            m.promptRestart = "Update angewendet. Serverneustart erforderlich.";
            m.startUpCheck = "Starte Update-Prüfung beim Start.";
            m.periodicCheck = "Regelmäßige Update-Prüfung geplant (alle {hours} Stunden).";
            m.adminLoginCheck = "Admin-Login erkannt; Update-Prüfung wird ausgeführt.";
            m.manualTriggered = "Manuelle Update-Prüfung gestartet.";
            m.nothingToDo = "Keine gültigen Ziele. Bitte config.yml prüfen.";
            m.done = "Update-Prüfung abgeschlossen.";
            m.noPermission = "Keine Berechtigung.";
            return m;
        }

        public static Messages korean() {
            Messages m = new Messages();
            m.prefix = "§a[GeyserUpdater]§r ";
            m.checking = "업데이트를 확인하는 중...";
            m.upToDate = "{project}은(는) 최신 상태입니다.";
            m.updated = "{project}을(를) 최신 빌드로 업데이트했습니다.";
            m.noTarget = "업데이트 대상 프로젝트가 활성화되어 있지 않습니다.";
            m.failed = "{project} 업데이트에 실패했습니다: {error}";
            m.promptRestart = "업데이트가 적용되었습니다. 서버 재시작이 필요합니다.";
            m.startUpCheck = "시작 시 자동 업데이트 확인을 시작합니다.";
            m.periodicCheck = "정기 업데이트 확인을 예약했습니다({hours}시간마다).";
            m.adminLoginCheck = "권한 보유자 로그인 감지, 업데이트 확인을 실행합니다.";
            m.manualTriggered = "수동 업데이트 확인을 시작합니다.";
            m.nothingToDo = "유효한 대상이 없습니다. config.yml을 확인하세요.";
            m.done = "업데이트 확인이 완료되었습니다.";
            m.noPermission = "권한이 없습니다.";
            return m;
        }
    }

    public static String normalizeLanguage(String language) {
        if (language == null) return "ja";
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return "ja";
        int sep = normalized.indexOf('-');
        if (sep < 0) sep = normalized.indexOf('_');
        if (sep > 0) normalized = normalized.substring(0, sep);
        return switch (normalized) {
            case "en", "de", "ko", "ja" -> normalized;
            default -> "ja";
        };
    }

    public static Messages defaultMessagesFor(String language) {
        String lang = normalizeLanguage(language);
        return switch (lang) {
            case "en" -> Messages.english();
            case "de" -> Messages.german();
            case "ko" -> Messages.korean();
            default -> new Messages();
        };
    }
}
