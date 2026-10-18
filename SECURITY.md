![MirrorCast — SECURITY](assets/06_SECURITY_md_banner.png)

# Security Policy

## Supported Versions

MirrorCast actively maintains and provides security updates for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

---

## Reporting a Vulnerability

The MirrorCast team takes security and privacy seriously. Because MirrorCast processes real-time screen data across local networks, vulnerabilities concerning unauthorized access, PIN bypass, or unintended streaming exposure are prioritized.

### How to Report
Please **do not** report security vulnerabilities through public GitHub issues.

Instead, please submit a Private Vulnerability Advisory at [https://github.com/0xSHARON/mirrorcast/security/advisories](https://github.com/0xSHARON/mirrorcast/security/advisories).

### What to Include
Please provide as much information as possible:
1. Type of issue (e.g., signaling authentication bypass, session replay, buffer overflow).
2. Affected component (Android Sender, Windows Receiver, or Signaling Protocol).
3. Exact steps to reproduce the vulnerability.
4. Proof-of-concept exploit code or network packet capture if available.
5. Potential impact and severity assessment.

### Response Timeline
- **Initial Acknowledgment**: Within 48 hours.
- **Triage & Reproduction**: Within 5 business days.
- **Remediation & Patch Release**: Dependent on complexity, targeting within 14 days for high-severity issues.
