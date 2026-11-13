# Model providers

The app is provider-agnostic because the agent underneath is. A provider profile
is four fields — endpoint, model, key, kind — and the kind decides which
environment variables the guest process receives.

## Built-in profiles

| Profile | Kind | Endpoint | Notes |
|---|---|---|---|
| OpenRouter | OpenAI-compatible | `https://openrouter.ai/api/v1` | one key, most models |
| Google Gemini | Gemini | `https://generativelanguage.googleapis.com` | free tier available |
| Local Ollama | Ollama | `http://<lan-ip>:11434/v1` | no key, works offline |

Add more by appending to `ProviderProfile.defaults()` or, once the editor UI
lands, from settings.

## Keys

Keys live in `EncryptedSharedPreferences` under the alias in the profile
(`key.openrouter`, `key.gemini`). The profile object itself stores only the alias,
so a settings dump or a crash report never contains a credential. `Logs.redact()`
strips `sk-`, `AIza` and `Bearer` patterns from anything that reaches logcat.

A key is passed to the guest process through the environment, never on the
command line: `/proc/<pid>/cmdline` is world readable on Android, the environment
is not.

## Choosing a model for a phone

Three constraints, in order:

1. **Context window.** The agent reads files. A 32k window means it will thrash on
   anything larger than a few hundred lines. 200k is the useful floor for real
   work.
2. **Tool-call reliability.** Streaming is table stakes; reliable parallel tool
   calls are what separate a usable agent from a chat toy.
3. **Latency per turn.** A turn is many round trips. A model that is twice as
   smart but four times slower feels worse on a phone.

For local use, a 7B coder model on a phone is a toy; on a LAN box it is useful.
Point the Ollama profile at a desktop, not at the phone itself.

## Adding a provider

1. Add a `Kind` if the wire format is not OpenAI-compatible.
2. Add a branch in `ProviderProfile.envFor`.
3. Add a default profile in `defaults()`.
4. If it needs a header the CLI cannot set, that is a CLI feature request, not an
   app hack. Do not patch around it in the launcher.
