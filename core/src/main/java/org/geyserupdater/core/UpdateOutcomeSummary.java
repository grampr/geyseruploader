package org.geyserupdater.core;

import java.util.ArrayList;
import java.util.List;

public final class UpdateOutcomeSummary {
    private UpdateOutcomeSummary() {
    }

    public static Summary summarize(Config cfg, List<UpdaterService.UpdateOutcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return new Summary(false, true, List.of(cfg.messages.nothingToDo));
        }

        boolean anyUpdated = false;
        List<String> messages = new ArrayList<>();
        for (UpdaterService.UpdateOutcome outcome : outcomes) {
            String project = outcome.project.name().toLowerCase();
            if (outcome.error.isPresent()) {
                messages.add(cfg.messages.failed
                        .replace("{project}", project)
                        .replace("{error}", outcome.error.get()));
                continue;
            }
            if (outcome.skippedNoChange) {
                messages.add(cfg.messages.upToDate.replace("{project}", project));
                continue;
            }
            if (outcome.updated) {
                anyUpdated = true;
                messages.add(cfg.messages.updated.replace("{project}", project));
            }
        }

        return new Summary(anyUpdated, false, messages);
    }

    public record Summary(boolean anyUpdated, boolean noTargets, List<String> messages) {
    }
}
