# Example output

These `.mid` files were produced by running the interpreter on the test
programs in [`../tests/`](../tests). Each one is a playable Standard MIDI File
(format 0) — open it in any media player, DAW, or notation app to hear it.

| File     | Generated from        | Command |
| -------- | --------------------- | ------- |
| `V1.mid` | `tests/V1.harm`       | `java -cp out Main tests/V1.harm --midi=examples/V1.mid --no-audio` |
| `V2.mid` | `tests/V2.harm`       | `java -cp out Main tests/V2.harm --midi=examples/V2.mid --no-audio` |
| `V3.mid` | `tests/V3.harm`       | `java -cp out Main tests/V3.harm --midi=examples/V3.mid --no-audio` |

To regenerate them yourself, compile the project (see the main README) and run
the commands above.
