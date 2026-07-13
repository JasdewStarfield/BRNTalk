package yourscraft.jasdewstarfield.brntalk.data;

import yourscraft.jasdewstarfield.brntalk.Brntalk;

import java.util.*;

final class ConversationValidator {

    private ConversationValidator() {}

    static ValidationReport validate(String sourceId, String convId, List<TalkMessage> messages, Set<String> duplicateMessageIds) {
        ValidationReport report = new ValidationReport(sourceId, convId);

        if (messages.isEmpty()) {
            report.error("Script contains no messages.");
            return report;
        }

        for (String duplicateId : duplicateMessageIds) {
            report.error(duplicateId, "Duplicate message id '" + duplicateId + "'.");
        }

        Set<String> validIds = new HashSet<>();
        for (TalkMessage msg : messages) {
            validIds.add(msg.getId());
        }

        for (TalkMessage msg : messages) {
            String next = msg.getNextId();
            if (next != null && !next.isEmpty() && !validIds.contains(next)) {
                report.error(msg.getId(), "Message '" + msg.getId() + "' points to missing nextId '" + next + "'.");
            }

            if (isBlank(msg.getText())) {
                report.warn(msg.getId(), "Message '" + msg.getId() + "' has empty or blank text.");
            }

            if (msg.getType() == TalkMessage.Type.CHOICE) {
                if (msg.getChoices().isEmpty()) {
                    report.error(msg.getId(), "Choice message '" + msg.getId() + "' has no choices.");
                    continue;
                }

                Set<String> choiceIds = new HashSet<>();
                for (TalkMessage.Choice choice : msg.getChoices()) {
                    if (isBlank(choice.getText())) {
                        report.warn(msg.getId(), choice.getId(), "Choice '" + choice.getId() + "' in message '" + msg.getId() + "' has empty or blank text.");
                    }

                    if (!choiceIds.add(choice.getId())) {
                        report.error(msg.getId(), choice.getId(), "Choice message '" + msg.getId() + "' has duplicate choice id '" + choice.getId() + "'.");
                    }

                    String choiceNext = choice.getNextId();
                    if (choiceNext != null && !choiceNext.isEmpty() && !validIds.contains(choiceNext)) {
                        report.error(msg.getId(), choice.getId(), "Choice '" + choice.getId() + "' in message '" + msg.getId() + "' points to missing nextId '" + choiceNext + "'.");
                    }
                }
            }

            if (msg.getType() == TalkMessage.Type.WAIT && (next == null || next.isEmpty())) {
                report.warn(msg.getId(), "Wait message '" + msg.getId() + "' has no nextId, so /brntalk resume cannot advance it.");
            }
        }

        validateReachability(messages, report);
        validateTextLoops(messages, report);
        return report;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void validateReachability(List<TalkMessage> messages, ValidationReport report) {
        if (messages.isEmpty()) {
            return;
        }

        Map<String, TalkMessage> msgMap = new HashMap<>();
        for (TalkMessage msg : messages) {
            msgMap.putIfAbsent(msg.getId(), msg);
        }

        Set<String> reachableIds = new HashSet<>();
        Deque<String> pendingIds = new ArrayDeque<>();
        pendingIds.add(messages.get(0).getId());

        while (!pendingIds.isEmpty()) {
            String currentId = pendingIds.removeFirst();
            if (!reachableIds.add(currentId)) {
                continue;
            }

            TalkMessage currentMsg = msgMap.get(currentId);
            if (currentMsg == null) {
                continue;
            }

            addReachableTarget(currentMsg.getNextId(), msgMap, reachableIds, pendingIds);
            for (TalkMessage.Choice choice : currentMsg.getChoices()) {
                addReachableTarget(choice.getNextId(), msgMap, reachableIds, pendingIds);
            }
        }

        // Unreachable nodes may be intentional author scratch space, so they
        // are warnings instead of hard load failures.
        for (TalkMessage msg : messages) {
            if (!reachableIds.contains(msg.getId())) {
                report.warn(msg.getId(), "Message '" + msg.getId() + "' is unreachable from the first message.");
            }
        }
    }

    private static void addReachableTarget(String targetId,
                                           Map<String, TalkMessage> msgMap,
                                           Set<String> reachableIds,
                                           Deque<String> pendingIds) {
        if (targetId != null && !targetId.isEmpty() && msgMap.containsKey(targetId) && !reachableIds.contains(targetId)) {
            pendingIds.add(targetId);
        }
    }

    private static void validateTextLoops(List<TalkMessage> messages, ValidationReport report) {
        Map<String, TalkMessage> msgMap = new HashMap<>();
        Map<String, Integer> visitState = new HashMap<>();

        for (TalkMessage msg : messages) {
            msgMap.put(msg.getId(), msg);
            visitState.put(msg.getId(), 0);
        }

        for (TalkMessage msg : messages) {
            if (msg.getType() == TalkMessage.Type.TEXT && visitState.get(msg.getId()) == 0) {
                detectLoopDfs(msg.getId(), msgMap, visitState, report);
            }
        }
    }

    private static boolean detectLoopDfs(String currentId,
                                         Map<String, TalkMessage> msgMap,
                                         Map<String, Integer> visitState,
                                         ValidationReport report) {
        visitState.put(currentId, 1);

        TalkMessage currentMsg = msgMap.get(currentId);
        if (currentMsg == null) {
            visitState.put(currentId, 2);
            return false;
        }

        if (currentMsg.getType() != TalkMessage.Type.TEXT) {
            visitState.put(currentId, 2);
            return false;
        }

        String nextId = currentMsg.getNextId();
        if (nextId != null && !nextId.isEmpty()) {
            Integer nextState = visitState.getOrDefault(nextId, 0);
            if (nextState == 1) {
                report.error(currentId, "Infinite TEXT auto-advance loop detected; the loop closes at message '" + currentId + "'.");
                return true;
            }

            if (nextState == 0 && detectLoopDfs(nextId, msgMap, visitState, report)) {
                return true;
            }
        }

        visitState.put(currentId, 2);
        return false;
    }

    static final class ValidationReport {
        enum Severity {
            ERROR,
            WARNING,
            SUGGESTION
        }

        private final String convId;
        private final String sourceId;
        private final List<ValidationIssue> issues = new ArrayList<>();

        ValidationReport(String sourceId, String convId) {
            this.sourceId = sourceId;
            this.convId = convId;
        }

        void warn(String warning) {
            addIssue(Severity.WARNING, null, null, warning);
        }

        void warn(String messageId, String warning) {
            addIssue(Severity.WARNING, messageId, null, warning);
        }

        void warn(String messageId, String choiceId, String warning) {
            addIssue(Severity.WARNING, messageId, choiceId, warning);
        }

        void error(String error) {
            addIssue(Severity.ERROR, null, null, error);
        }

        void error(String messageId, String error) {
            addIssue(Severity.ERROR, messageId, null, error);
        }

        void error(String messageId, String choiceId, String error) {
            addIssue(Severity.ERROR, messageId, choiceId, error);
        }

        private void addIssue(Severity severity, String messageId, String choiceId, String summary) {
            issues.add(new ValidationIssue(severity, sourceId, convId, messageId, choiceId, summary));
        }

        boolean hasErrors() {
            return errorCount() > 0;
        }

        int errorCount() {
            int count = 0;
            for (ValidationIssue issue : issues) {
                if (issue.severity() == Severity.ERROR) {
                    count++;
                }
            }
            return count;
        }

        String convId() {
            return convId;
        }

        String sourceId() {
            return sourceId;
        }

        List<ValidationIssue> issues() {
            return List.copyOf(issues);
        }

        List<ValidationIssue> errors() {
            List<ValidationIssue> errors = new ArrayList<>();
            for (ValidationIssue issue : issues) {
                if (issue.severity() == Severity.ERROR) {
                    errors.add(issue);
                }
            }
            return List.copyOf(errors);
        }

        void logProblems() {
            for (ValidationIssue issue : issues) {
                if (issue.severity() == Severity.ERROR) {
                    Brntalk.LOGGER.error("[BRNTalk] Validation: {}", issue.toLogLine());
                } else if (issue.severity() == Severity.WARNING) {
                    Brntalk.LOGGER.warn("[BRNTalk] Validation: {}", issue.toLogLine());
                } else {
                    Brntalk.LOGGER.info("[BRNTalk] Validation: {}", issue.toLogLine());
                }
            }
        }
    }

    static final class ValidationIssue {
        private final ValidationReport.Severity severity;
        private final String sourceId;
        private final String scriptId;
        private final String messageId;
        private final String choiceId;
        private final String summary;

        private ValidationIssue(ValidationReport.Severity severity,
                                String sourceId,
                                String scriptId,
                                String messageId,
                                String choiceId,
                                String summary) {
            this.severity = severity;
            this.sourceId = sourceId;
            this.scriptId = scriptId;
            this.messageId = messageId;
            this.choiceId = choiceId;
            this.summary = summary;
        }

        ValidationReport.Severity severity() {
            return severity;
        }

        String sourceId() {
            return sourceId;
        }

        String scriptId() {
            return scriptId;
        }

        String messageId() {
            return messageId;
        }

        String choiceId() {
            return choiceId;
        }

        String summary() {
            return summary;
        }

        // Keep the log line self-contained so authors can jump from latest.log
        // back to the exact resource, script, message, and choice that failed.
        String toLogLine() {
            StringBuilder builder = new StringBuilder(severity.name())
                    .append(" source='").append(sourceId).append("'")
                    .append(" script='").append(scriptId).append("'");
            if (messageId != null) {
                builder.append(" message='").append(messageId).append("'");
            }
            if (choiceId != null) {
                builder.append(" choice='").append(choiceId).append("'");
            }
            return builder.append(": ").append(summary).toString();
        }
    }
}
