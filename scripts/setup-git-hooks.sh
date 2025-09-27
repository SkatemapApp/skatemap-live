#!/bin/bash

# Setup script to install git hooks for the project
# This script installs commit message validation for conventional commits

set -e

echo "Setting up git hooks..."

# Copy commit-msg hook
cp scripts/git-hooks/commit-msg .git/hooks/commit-msg
chmod +x .git/hooks/commit-msg

echo "✅ Git hooks installed successfully!"
echo ""
echo "The following hooks are now active:"
echo "  - commit-msg: Validates conventional commit message format"
echo "  - pre-commit: Formats Scala files with scalafmt (already installed)"
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