# Contributing to LAN-cam App

First off, thank you for considering contributing to LAN-cam App! It's people like you who make it a great tool for everyone.

## Code of Conduct
Please be respectful and professional in all interactions.

## How Can I Contribute?

### Reporting Bugs
- Use GitHub Issues to report bugs.
- Provide a clear title and description, as much relevant information as possible, and a code sample or a test case demonstrating the expected behavior that is not occurring.

### Suggesting Enhancements
- Check the [ISSUES.md](ISSUES.md) or GitHub Issues to see if the enhancement has already been suggested.
- If not, open a new issue to discuss the change before implementing it.

### Pull Requests
1. **Branching Strategy**:
   - `main`: Stable release branch.
   - `develop`: Active development branch. All PRs should be targeted at `develop`.
   - `feature/*`: For new features or significant changes.
2. **Setup**:
   - Fork the repository and create your branch from `develop`.
   - Ensure your code follows the existing style and is well-documented.
3. **Submission**:
   - Open a Pull Request against the `develop` branch.
   - Reference the related issue in your PR description.

## Coding Standards
- Use Kotlin for all new logic.
- Follow standard Android development best practices.
- Ensure any new UI uses Jetpack Compose.

## Commit Messages
- Use clear and concise commit messages.
- Example: `feat: add support for HLS streams` or `fix: resolve crash on 16kb devices`.
