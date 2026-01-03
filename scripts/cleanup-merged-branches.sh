#!/bin/bash

set -e

MAIN_BRANCH="master"
REMOTE="origin"
DRY_RUN=false

show_help() {
    echo "Usage: cleanup-merged-branches.sh [OPTIONS]"
    echo ""
    echo "Clean up local git branches that have been merged into master."
    echo ""
    echo "This script identifies branches that have been:"
    echo "  - Merged into origin/master (regular or squash merge)"
    echo "  - Deleted from the remote repository"
    echo ""
    echo "Protected branches (never deleted):"
    echo "  - master"
    echo "  - main"
    echo "  - Current branch"
    echo ""
    echo "Options:"
    echo "  --remote <name>  Specify remote name (default: origin)"
    echo "  --dry-run, -n    Show which branches would be deleted without deleting them"
    echo "  --help, -h       Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./scripts/cleanup-merged-branches.sh           # Interactive mode"
    echo "  ./scripts/cleanup-merged-branches.sh --dry-run # Preview mode"
    echo ""
}

get_current_branch() {
    git rev-parse --abbrev-ref HEAD
}

is_protected() {
    local branch=$1
    local current_branch=$2

    [[ "$branch" == "master" ]] && return 0
    [[ "$branch" == "main" ]] && return 0
    [[ "$branch" == "$current_branch" ]] && return 0

    return 1
}

find_stale_branches() {
    git for-each-ref --format='%(refname:short) %(upstream:track)' refs/heads/ 2>/dev/null | \
        awk '$2 == "[gone]" {printf "%s%c", $1, 0}' || true
}

find_merged_branches() {
    local branches
    if ! branches=$(git branch --merged "$REMOTE/$MAIN_BRANCH" 2>&1); then
        echo "Warning: Could not determine merged branches" >&2
        return 0
    fi

    echo "$branches" | sed 's/^[* ]*//' | while IFS= read -r branch; do
        [[ -n "$branch" ]] && printf '%s\0' "$branch"
    done
}

collect_deletable_branches() {
    local current_branch=$1
    local temp_file
    temp_file=$(mktemp)

    find_stale_branches >> "$temp_file"
    find_merged_branches >> "$temp_file"

    sort -uz "$temp_file" | while IFS= read -r -d '' branch; do
        if [[ -n "$branch" ]] && ! is_protected "$branch" "$current_branch"; then
            printf '%s\0' "$branch"
        fi
    done

    rm -f "$temp_file"
}

display_and_confirm() {
    local current_branch=$1
    shift
    local branches=("$@")
    local count=${#branches[@]}

    if [[ $count -eq 0 ]]; then
        echo "✅ No merged or stale branches found. Your repository is clean!"
        return 1
    fi

    if [[ "$DRY_RUN" == true ]]; then
        echo "[DRY RUN] The following branches would be deleted:"
    else
        echo "Found $count branch(es) that can be deleted:"
    fi

    for branch in "${branches[@]}"; do
        echo "  - $branch"
    done
    echo ""

    local protected="master, main"
    if [[ "$current_branch" != "master" ]] && [[ "$current_branch" != "main" ]]; then
        protected="$current_branch, $protected"
    fi
    echo "Protected: $protected"
    echo ""

    if [[ "$DRY_RUN" == true ]]; then
        echo "No branches were deleted (dry-run mode)."
        return 1
    fi

    read -p "Delete these $count branch(es)? (y/N): " -r
    echo ""

    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Cancelled. No branches were deleted."
        return 1
    fi

    return 0
}

delete_branches() {
    local -a branches=("$@")
    local success=0
    local failed=0

    echo "Deleting branches..."

    for branch in "${branches[@]}"; do
        if git branch -D "$branch" >/dev/null 2>&1; then
            echo "  ✅ Deleted $branch"
            success=$((success + 1))
        else
            echo "  ❌ Failed to delete $branch"
            failed=$((failed + 1))
        fi
    done

    echo ""

    if [[ $failed -eq 0 ]]; then
        echo "✅ Successfully deleted $success branch(es)"
    else
        echo "Deleted $success of $((success + failed)) branch(es)."
        echo "❌ $failed branch(es) failed to delete"
    fi
}

main() {
    if ! git rev-parse --git-dir > /dev/null 2>&1; then
        echo "❌ Error: Not a git repository"
        exit 1
    fi

    echo "Fetching latest remote information..."
    if ! git fetch --prune "$REMOTE" >/dev/null 2>&1; then
        echo "❌ Error: Cannot fetch from remote"
        exit 1
    fi
    echo "✅ Remote information updated"
    echo ""

    local current_branch
    current_branch=$(get_current_branch)

    echo "Scanning for merged and stale branches..."
    echo ""

    local temp_branches
    temp_branches=$(mktemp)
    collect_deletable_branches "$current_branch" > "$temp_branches"

    local -a deletable_branches
    while IFS= read -r -d '' branch; do
        deletable_branches+=("$branch")
    done < "$temp_branches"
    rm -f "$temp_branches"

    if display_and_confirm "$current_branch" "${deletable_branches[@]}"; then
        delete_branches "${deletable_branches[@]}"
    fi
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --help|-h)
            show_help
            exit 0
            ;;
        --remote)
            shift
            if [[ -z "$1" ]]; then
                echo "Error: --remote requires a remote name"
                exit 1
            fi
            REMOTE="$1"
            ;;
        --dry-run|-n)
            DRY_RUN=true
            ;;
        *)
            echo "Unknown option: $1"
            echo ""
            show_help
            exit 1
            ;;
    esac
    shift
done

main
