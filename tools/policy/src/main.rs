//! Sandbox policy evaluator.
//!
//! The app ships a permissive default policy and this tool is how that policy gets
//! reviewed and tested. It answers one question: given a command the agent wants to
//! run, is it allowed, denied, or does it need the user's confirmation?
//!
//! Deliberately dependency free, so it builds on any CI runner in under a second.
//!
//! Usage:
//!   ocm-policy --policy policy.json --command "git status"
//!   ocm-policy --selftest

use std::env;
use std::fs;
use std::process::ExitCode;

#[derive(Debug, Clone, PartialEq)]
enum Verdict {
    Allow,
    Confirm,
    Deny,
}

impl Verdict {
    fn as_str(&self) -> &'static str {
        match self {
            Verdict::Allow => "allow",
            Verdict::Confirm => "confirm",
            Verdict::Deny => "deny",
        }
    }
}

#[derive(Debug)]
struct Rule {
    prefix: String,
    verdict: Verdict,
}

#[derive(Debug)]
struct Policy {
    rules: Vec<Rule>,
    default_verdict: Verdict,
}

/// Words that never need a second look.
const SAFE_HEADS: &[&str] = &[
    "ls", "cat", "head", "tail", "grep", "rg", "find", "wc", "pwd", "echo",
    "git status", "git diff", "git log", "node --version", "python3 --version",
];

/// Words that are denied outright, regardless of policy file.
///
/// This list is intentionally short and blunt. A long denylist invites the reader
/// to believe it is complete; it is not, and the real containment is proot.
const HARD_DENY: &[&str] = &[
    "rm -rf /", "mkfs", "dd if=/dev/zero", ":(){", "shutdown", "reboot",
    "pm uninstall", "settings put global", "su -c",
];

fn parse_policy(text: &str) -> Policy {
    let mut rules = Vec::new();
    let mut default_verdict = Verdict::Confirm;

    for line in text.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        // Format: "<verdict> <command prefix>"
        let mut parts = trimmed.splitn(2, ' ');
        let verdict = match parts.next() {
            Some("allow") => Verdict::Allow,
            Some("confirm") => Verdict::Confirm,
            Some("deny") => Verdict::Deny,
            Some("default") => {
                default_verdict = match parts.next() {
                    Some("allow") => Verdict::Allow,
                    Some("deny") => Verdict::Deny,
                    _ => Verdict::Confirm,
                };
                continue;
            }
            _ => continue,
        };
        if let Some(prefix) = parts.next() {
            rules.push(Rule {
                prefix: prefix.trim().to_string(),
                verdict,
            });
        }
    }

    Policy {
        rules,
        default_verdict,
    }
}

fn evaluate(policy: &Policy, command: &str) -> Verdict {
    let normalized = command.trim().to_lowercase();

    for denied in HARD_DENY {
        if normalized.contains(denied) {
            return Verdict::Deny;
        }
    }

    // Longest matching prefix wins, so a specific rule can carve out an exception
    // to a broader one ("confirm git" + "allow git status").
    let mut best: Option<&Rule> = None;
    for rule in &policy.rules {
        if normalized.starts_with(&rule.prefix.to_lowercase()) {
            if best.map_or(true, |b| rule.prefix.len() > b.prefix.len()) {
                best = Some(rule);
            }
        }
    }
    if let Some(rule) = best {
        return rule.verdict.clone();
    }

    for safe in SAFE_HEADS {
        if normalized.starts_with(safe) {
            return Verdict::Allow;
        }
    }

    policy.default_verdict.clone()
}

fn selftest() -> bool {
    let policy = parse_policy(
        "default confirm\nallow git status\nallow ls\ndeny curl\n",
    );

    let cases = vec![
        ("ls -la", Verdict::Allow),
        ("git status", Verdict::Allow),
        ("git push --force", Verdict::Confirm),
        ("curl http://localhost", Verdict::Deny),
        ("rm -rf /", Verdict::Deny),
        ("make build", Verdict::Confirm),
        ("grep -r foo .", Verdict::Allow),
    ];

    let mut ok = true;
    for (command, expected) in cases {
        let actual = evaluate(&policy, command);
        if actual != expected {
            println!(
                "FAIL  {:>24}  expected {}, got {}",
                command,
                expected.as_str(),
                actual.as_str()
            );
            ok = false;
        } else {
            println!("ok    {:>24}  {}", command, actual.as_str());
        }
    }
    ok
}

fn main() -> ExitCode {
    let args: Vec<String> = env::args().skip(1).collect();

    if args.iter().any(|a| a == "--selftest") {
        return if selftest() {
            ExitCode::SUCCESS
        } else {
            ExitCode::FAILURE
        };
    }

    let mut policy_path = String::from("policy.txt");
    let mut command = String::new();
    let mut index = 0;
    while index < args.len() {
        match args[index].as_str() {
            "--policy" => {
                policy_path = args.get(index + 1).cloned().unwrap_or_default();
                index += 2;
            }
            "--command" => {
                command = args.get(index + 1).cloned().unwrap_or_default();
                index += 2;
            }
            _ => index += 1,
        }
    }

    if command.is_empty() {
        eprintln!("usage: ocm-policy --policy <file> --command <cmd> | --selftest");
        return ExitCode::from(2);
    }

    let text = fs::read_to_string(&policy_path).unwrap_or_default();
    let policy = parse_policy(&text);
    let verdict = evaluate(&policy, &command);

    println!("{}", verdict.as_str());
    match verdict {
        Verdict::Deny => ExitCode::from(1),
        _ => ExitCode::SUCCESS,
    }
}
