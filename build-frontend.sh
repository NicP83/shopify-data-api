#!/bin/bash

# Build and Deploy React Frontend to Spring Boot
# This script builds the React app and copies it to Spring Boot's static resources

set -e  # Exit on any error

echo "🚀 Building React Frontend for Production..."

# Navigate to frontend directory
cd frontend

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "📦 Installing npm dependencies..."
    npm install
fi

# Build React app
echo "🔨 Building React app..."
npm run build

# Create static resources directory if it doesn't exist
echo "📁 Creating static resources directory..."
mkdir -p ../src/main/resources/static

# Clear old static files
echo "🧹 Cleaning old static files..."
rm -rf ../src/main/resources/static/*

# Copy build files to Spring Boot static resources
echo "📋 Copying build files to Spring Boot..."
cp -r dist/* ../src/main/resources/static/

echo "✅ Frontend build complete!"
echo "📦 Files copied to: src/main/resources/static/"

# Return to root directory
cd ..

echo ""
echo "Next steps:"
echo "1. Build the Spring Boot app: mvn clean package -DskipTests"
echo "2. Run locally: mvn spring-boot:run"
echo "3. Deploy to Railway: git add -A && git commit -m 'Add React frontend' && git push"
