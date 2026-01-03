#!/bin/bash

# Setup script to install git hooks for the project
# This script installs commit message validation for conventional commits

set -e

echo "Setting up git hooks..."

# Copy hooks
cp scripts/git-hooks/commit-msg .git/hooks/commit-msg
cp scripts/git-hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/commit-msg
chmod +x .git/hooks/pre-commit

echo "✅ Git hooks installed successfully!"
echo ""
echo "The following hooks are now active:"
echo "  - pre-commit: Formats Scala, Go, and shell files"
echo "  - commit-msg: Validates conventional commit message format"
echo ""
echo "Your commit messages must now follow the conventional commits format:"
echo "  https://www.conventionalcommits.org/en/v1.0.0/"
echo ""
echo "Valid prefixes: build, chore, ci, docs, feat, fix, perf, refactor, style, test"
echo ""
echo "Examples:"
echo "  feat: add user authentication"
echo "  fix: resolve memory leak in data processor"
echo "  docs: update API documentation"
echo "  feat(auth): implement OAuth2 login"
echo ""
echo "Prerequisites for pre-commit hook:"
echo "  - sbt (for Scala formatting)"
echo "  - goimports (for Go formatting)"
echo "    Install with: cd tools/load-testing && make install-tools"
echo "  - shfmt (for shell script formatting)"
echo "    Install with: brew install shfmt"
echo ""

if command -v shfmt &>/dev/null; then
  echo "✅ shfmt is installed and ready"
else
  echo "⚠️  shfmt not found - install with: brew install shfmt"
fi
echo ""

if [[ ":$PATH:" != *":$HOME/go/bin:"* ]]; then
  echo "⚠️  WARNING: ~/go/bin is not in your PATH"
  echo "   Go tools will not be found by the pre-commit hook"
  echo "   Add to your shell profile (.bashrc, .zshrc, etc.):"
  echo "   export PATH=\"\$HOME/go/bin:\$PATH\""
  echo ""
fi
