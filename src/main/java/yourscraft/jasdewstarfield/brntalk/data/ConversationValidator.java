package yourscraft.jasdewstarfield.brntalk.data;

import yourscraft.jasdewstarfield.brntalk.Brntalk;

import java.util.*;

final class ConversationValidator {

    private ConversationValidator() {}

    static ValidationReport validate(String convId, List<TalkMessage> messages, Set<String> duplicateMessageIds) {
        ValidationReport report = new ValidationReport(convId);

        if (messages.isEmpty()) {
            report.error("Script contains no messages.");
            return report;
        }

        for (String duplicateId : duplicateMessageIds) {
            report.error("Duplicate message id '" + duplicateId + "'.");
        }

        Set<String> validIds = new HashSet<>();
        for (TalkMessage msg : messages) {
            validIds.add(msg.getId());
        }

        for (TalkMessage msg : messages) {
            String next = msg.getNextId();
            if (next != null && !next.isEmpty() && !validIds.contains(next)) {
                report.error("Message '" + msg.getId() + "' points to missing nextId '" + next + "'.");
            }

            if (msg.getType() == TalkMessage.Type.CHOICE) {
                if (msg.getChoices().isEmpty()) {
                    report.error("Choice message '" + msg.getId() + "' has no choices.");
                    continue;
                }

                Set<String> choiceIds = new HashSet<>();
                for (TalkMessage.Choice choice : msg.getChoices()) {
                    if (!choiceIds.add(choice.getId())) {
                        report.error("Choice message '" + msg.getId() + "' has duplicate choice id '" + choice.getId() + "'.");
                    }

                    String choiceNext = choice.getNextId();
                    if (choiceNext != null && !choiceNext.isEmpty() && !validIds.contains(choiceNext)) {
                        report.error("Choice '" + choice.getId() + "' in message '" + msg.getId() + "' points to missing nextId '" + choiceNext + "'.");
                    }
                }
            }
        }

        validateTextLoops(messages, report);
        return report;
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
                report.error("Infinite TEXT auto-advance loop detected; the loop closes at message '" + currentId + "'.");
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
        private final String convId;
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        ValidationReport(String convId) {
            this.convId = convId;
        }

        void warn(String warning) {
            warnings.add(warning);
        }

        void error(String error) {
            errors.add(error);
        }

        boolean hasErrors() {
            return !errors.isEmpty();
        }

        int errorCount() {
            return errors.size();
        }

        String convId() {
            return convId;
        }

        List<String> errors() {
            return List.copyOf(errors);
        }

        void logProblems() {
            for (String warning : warnings) {
                Brntalk.LOGGER.warn("[BRNTalk] Validation: Script '{}': {}", convId, warning);
            }
            for (String error : errors) {
                Brntalk.LOGGER.error("[BRNTalk] Validation: Script '{}': {}", convId, error);
            }
        }
    }
}
