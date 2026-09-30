# Security Policy

[English](SECURITY.md) • [简体中文](SECURITY.zh-CN.md) • [日本語](SECURITY.ja.md)

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

---

## Reporting a Vulnerability

Security is a high priority for **MaiNF**, particularly regarding remote server connections and the embedded FTP/SMB daemon features.

If you discover a security vulnerability or path traversal issue:

1. **Do not disclose the issue publicly** on GitHub Issues or social media.
2. Please report the vulnerability privately via GitHub Security Advisories or by contacting the project maintainer directly.
3. Include detailed steps to reproduce the vulnerability, including device model, Android version, and network configuration.

We will acknowledge receipt within 48 hours and work with you to test and release a security patch promptly.

---

## Built-in Security Architecture

MaiNF incorporates several layers of defense-in-depth:

* **Strict Path Sandboxing**: All incoming file and directory requests on embedded FTP and SMB services are resolved against the user-designated shared root directory. The canonical path of the target must match the canonical path of the root directory or begin with the root directory followed by a path separator (`/`). This strictly prevents path-traversal escapes via `../` sequences or sibling prefix attacks.
* **Non-Root Port Execution**: Default ports are unprivileged (`2121` for FTP, `4450` for SMB), running safely within the standard Android application sandbox without requiring system root privileges.
* **Granular Read-Only Mode**: Users can restrict host access to read-only, preventing remote write, deletion, or modification of stored files.
* **Android FileProvider Isolation**: Local file sharing utilizes Android's secure `FileProvider` with temporary URI read permissions rather than direct file path exposure.
