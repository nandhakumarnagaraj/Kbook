#!/bin/bash

# Quick Sync Script for KhanaBook
# Usage: ./sync.sh "your commit message"

COMMIT_MSG=$1

if [ -z "$COMMIT_MSG" ]; then
    COMMIT_MSG="Auto-sync: $(date '+%Y-%m-%d %H:%M:%S')"
fi

echo "--- 🛠️  Starting Sync Process ---"

# 1. Fetch latest changes from GitHub
echo "--- 📥 Pulling latest changes ---"
git pull origin main --rebase

# 2. Stage all local changes (if any)
echo "--- 📤 Staging local changes ---"
git add .

# 3. Check if there are changes to commit
if ! git diff --cached --quiet; then
    echo "--- 💾 Committing changes: $COMMIT_MSG ---"
    git commit -m "$COMMIT_MSG"
    
    echo "--- 🚀 Pushing to GitHub (Triggers Auto-Deploy) ---"
    git push origin main
else
    echo "--- ✅ No local changes to push ---"
fi

echo "--- ✨ Sync Complete! ---"
