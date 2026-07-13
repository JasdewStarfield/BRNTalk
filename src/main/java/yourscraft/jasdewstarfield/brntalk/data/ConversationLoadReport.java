package yourscraft.jasdewstarfield.brntalk.data;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class ConversationLoadReport {
    private static final ConversationLoadReport EMPTY = new ConversationLoadReport(0, 0, List.of(), List.of());

    private final int loadedConversations;
    private final int skippedConversations;
    private final List<InvalidConversation> invalidConversations;
    private final List<FileLoadFailure> fileLoadFailures;

    private ConversationLoadReport(int loadedConversations,
                                   int skippedConversations,
                                   List<InvalidConversation> invalidConversations,
                                   List<FileLoadFailure> fileLoadFailures) {
        this.loadedConversations = loadedConversations;
        this.skippedConversations = skippedConversations;
        this.invalidConversations = List.copyOf(invalidConversations);
        this.fileLoadFailures = List.copyOf(fileLoadFailures);
    }

    public static ConversationLoadReport empty() {
        return EMPTY;
    }

    public int loadedConversations() {
        return loadedConversations;
    }

    public int skippedConversations() {
        return skippedConversations;
    }

    public List<InvalidConversation> invalidConversations() {
        return invalidConversations;
    }

    public List<FileLoadFailure> fileLoadFailures() {
        return fileLoadFailures;
    }

    public int failedFileCount() {
        return fileLoadFailures.size();
    }

    public boolean hasProblems() {
        return skippedConversations > 0 || !fileLoadFailures.isEmpty();
    }

    public int totalProblemCount() {
        int total = fileLoadFailures.size();
        for (InvalidConversation conversation : invalidConversations) {
            total += conversation.errorCount();
        }
        return total;
    }

    public static final class InvalidConversation {
        private final String sourceId;
        private final String scriptId;
        private final List<ConversationValidator.ValidationIssue> issues;

        private InvalidConversation(String sourceId, String scriptId, List<ConversationValidator.ValidationIssue> issues) {
            this.sourceId = sourceId;
            this.scriptId = scriptId;
            this.issues = List.copyOf(issues);
        }

        public String sourceId() {
            return sourceId;
        }

        public String scriptId() {
            return scriptId;
        }

        public List<ConversationValidator.ValidationIssue> issues() {
            return issues;
        }

        public List<ConversationValidator.ValidationIssue> errors() {
            List<ConversationValidator.ValidationIssue> errors = new ArrayList<>();
            for (ConversationValidator.ValidationIssue issue : issues) {
                if (issue.severity() == ConversationValidator.ValidationReport.Severity.ERROR) {
                    errors.add(issue);
                }
            }
            return List.copyOf(errors);
        }

        public int errorCount() {
            int count = 0;
            for (ConversationValidator.ValidationIssue issue : issues) {
                if (issue.severity() == ConversationValidator.ValidationReport.Severity.ERROR) {
                    count++;
                }
            }
            return count;
        }
    }

    public static final class FileLoadFailure {
        private final String resourceId;
        private final String summary;

        private FileLoadFailure(String resourceId, String summary) {
            this.resourceId = resourceId;
            this.summary = summary;
        }

        public String resourceId() {
            return resourceId;
        }

        public String summary() {
            return summary;
        }
    }

    public static final class Builder {
        private int loadedConversations;
        private int skippedConversations;
        private final List<InvalidConversation> invalidConversations = new ArrayList<>();
        private final List<FileLoadFailure> fileLoadFailures = new ArrayList<>();

        public void incrementLoaded() {
            loadedConversations++;
        }

        public void addInvalidConversation(ConversationValidator.ValidationReport report) {
            skippedConversations++;
            invalidConversations.add(new InvalidConversation(report.sourceId(), report.convId(), report.errors()));
        }

        // Keep file-level parse/load failures in the same report as script validation
        // so the reload notifier can show one unified summary to admins.
        public void addFileLoadFailure(ResourceLocation resourceId, Exception exception) {
            String message = exception.getMessage();
            if (message != null) {
                message = message.replaceAll("\\s+", " ").trim();
            }

            String summary = exception.getClass().getSimpleName();
            if (message != null && !message.isEmpty()) {
                summary = summary + ": " + message;
            }

            fileLoadFailures.add(new FileLoadFailure(resourceId.toString(), summary));
        }

        public ConversationLoadReport build() {
            return new ConversationLoadReport(loadedConversations, skippedConversations, invalidConversations, fileLoadFailures);
        }
    }
}
