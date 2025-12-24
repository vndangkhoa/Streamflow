#!/bin/bash
APK_SOURCE="frontend/android/app/build/outputs/apk/debug/app-debug.apk"
APK_DEST="backend/static/StreamFlix-Universal-v1.0.8.apk"
HTML_FILE="backend/static/download.html"

echo "🚀 Deploying Android APK v1.0.8..."

# 1. Check if APK exists
if [ ! -f "$APK_SOURCE" ]; then
    echo "❌ APK build not found at $APK_SOURCE"
    echo "   Please wait for the build to finish."
    exit 1
fi

# 2. Move and Rename APK
echo "📦 Moving APK to static folder..."
cp "$APK_SOURCE" "$APK_DEST"
# Remove old APK if exists
rm -f "backend/static/StreamFlix-Universal-v1.0.6.apk"

# 3. Update Download Page
echo "📝 Updating download.html..."
# Use perl for cross-platform regex replacement (handles Mac/Linux nuances better than sed)
perl -i -pe 's/v1\.0\.6/v1.0.8/g' "$HTML_FILE"

# 4. Git Commit & Push
echo "octocat: Committing to GitHub..."
git add "$APK_DEST" "$HTML_FILE"
git commit -m "v1.0.8: Added Android APK to static assets"
git push origin main

echo ""
echo "✅ DEPLOYMENT SUCCESSFUL!"
echo "------------------------------------------------"
echo "👉 Next Step: Update your NAS Docker container."
echo "   docker pull vndangkhoa/streamflix:1.0.8"
echo "   (or rebuild if you are building locally)"
echo "------------------------------------------------"
