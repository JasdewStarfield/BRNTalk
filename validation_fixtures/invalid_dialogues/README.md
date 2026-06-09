# Invalid Dialogue Fixtures

These files are intentionally invalid BRNTalk dialogue scripts for validation testing.

They are stored outside `src/main/resources`, so they are not packaged into the mod jar.

Suggested usage:

1. Copy one file at a time into a real datapack dialogue location.
2. Run `/reload` or restart the server.
3. Confirm that BRNTalk logs a validation error and skips loading that script.

Files included:

- `duplicate_message_id.json`
- `missing_next_id.json`
- `empty_choice_node.json`
- `duplicate_choice_id.json`
- `infinite_text_loop.json`
