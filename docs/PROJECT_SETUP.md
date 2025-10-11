# Project Setup Guide

This guide walks you through setting up the Shopify Data API project from scratch.

## Prerequisites

### Required Software

1. **Java Development Kit (JDK) 17 or higher**
   ```bash
   # Check your Java version
   java -version

   # Should output: java version "17.0.x" or higher
   ```

   Download from: https://adoptium.net/

2. **Apache Maven 3.6+**
   ```bash
   # Check Maven version
   mvn -version

   # Should output: Apache Maven 3.6.x or higher
   ```

   Download from: https://maven.apache.org/download.cgi

3. **Git**
   ```bash
   git --version
   ```

4. **IDE (Optional but Recommended)**
   - IntelliJ IDEA (Community or Ultimate)
   - Eclipse
   - VS Code with Java extensions

### Required Accounts

1. **Shopify Partner Account** - https://partners.shopify.com/
2. **Development Shopify Store** - Create through Partner Dashboard
3. **Railway Account** - https://railway.com/ (for deployment)
4. **GitHub Account** - For code hosting

## Initial Setup

### 1. Clone or Download the Project

```bash
# If using Git
git clone <your-repo-url>
cd shopify-data-api

# Or download and extract the ZIP file
```

### 2. Project Structure Overview

```
shopify-data-api/
├── pom.xml                    # Maven configuration
├── src/
│   └── main/
│       ├── java/              # Java source code
│       └── resources/         # Configuration files
├── .env.example               # Environment variable template
└── README.md
```

### 3. Configure Environment Variables

Create a `.env` file for local development:

```bash
# Copy the example file
cp .env.example .env
```

Edit `.env` with your credentials (we'll get these in the next guide):

```bash
# Shopify Configuration
SHOPIFY_SHOP_URL=your-store.myshopify.com
SHOPIFY_ACCESS_TOKEN=shpat_xxxxxxxxxxxxxxxxxxxxx
SHOPIFY_API_VERSION=2025-01

# Database (for local development, use these defaults)
DATABASE_URL=jdbc:postgresql://localhost:5432/shopify_data
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres

# Application
PORT=8080
SHOPIFY_MAX_POINTS=100
```

### 4. Install PostgreSQL Locally (Optional)

For full local development:

**macOS (using Homebrew):**
```bash
brew install postgresql@15
brew services start postgresql@15
createdb shopify_data
```

**Windows:**
- Download from https://www.postgresql.org/download/windows/
- Install and create database named `shopify_data`

**Linux:**
```bash
sudo apt-get install postgresql-15
sudo systemctl start postgresql
sudo -u postgres createdb shopify_data
```

**Or use Railway's PostgreSQL** (skip local install):
- You can skip local PostgreSQL and just use Railway's database
- Update your `.env` with Railway's DATABASE_URL when deploying

## Building the Project

### 1. Install Dependencies

```bash
mvn clean install
```

This will:
- Download all required dependencies
- Compile the code
- Run tests (if any)
- Create a JAR file in `target/`

### 2. Verify the Build

Check for the compiled JAR:

```bash
ls target/
# Should see: shopify-data-api-1.0.0.jar
```

## Running Locally

### Option 1: Using Maven

```bash
mvn spring-boot:run
```

### Option 2: Using the JAR file

```bash
java -jar target/shopify-data-api-1.0.0.jar
```

### Option 3: From IDE

1. Open project in IntelliJ IDEA or Eclipse
2. Find `ShopifyDataApiApplication.java`
3. Right-click → Run

## Verify the Application

### 1. Check if it's running

```bash
curl http://localhost:8080/api/health
```

Expected response:
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "status": "UP",
    "service": "Shopify Data API",
    "timestamp": 1234567890
  }
}
```

### 2. Test Shopify Connection

```bash
curl http://localhost:8080/api/status
```

If Shopify credentials are configured correctly:
```json
{
  "success": true,
  "message": "System operational",
  "data": {
    "service": "Shopify Data API",
    "shopify_connected": true,
    "rate_limiter_available_points": 100
  }
}
```

## IDE Setup

### IntelliJ IDEA

1. **Import Project**
   - File → Open → Select `shopify-data-api` folder
   - IntelliJ will auto-detect Maven and import

2. **Enable Lombok**
   - File → Settings → Plugins
   - Search for "Lombok" and install
   - Enable annotation processing: Settings → Build → Compiler → Annotation Processors

3. **Set up Run Configuration**
   - Run → Edit Configurations
   - Add new "Application"
   - Main class: `com.shopify.api.ShopifyDataApiApplication`
   - Environment variables: Load from `.env`

### VS Code

1. **Install Extensions**
   - Extension Pack for Java
   - Spring Boot Extension Pack
   - Lombok Annotations Support

2. **Open Project**
   - File → Open Folder → Select `shopify-data-api`

3. **Run/Debug**
   - Open `ShopifyDataApiApplication.java`
   - Click "Run" above the main method

## Common Setup Issues

### Issue: `JAVA_HOME` not set

**Solution:**
```bash
# macOS/Linux
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Windows
setx JAVA_HOME "C:\Program Files\Java\jdk-17"
```

### Issue: Maven command not found

**Solution:**
- Ensure Maven is in your PATH
- Or use the Maven wrapper: `./mvnw` (macOS/Linux) or `mvnw.cmd` (Windows)

### Issue: Port 8080 already in use

**Solution:**
```bash
# Change port in .env
PORT=8081

# Or find and kill the process using 8080
# macOS/Linux:
lsof -ti:8080 | xargs kill -9

# Windows:
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Issue: Database connection failed

**Solution:**
- Ensure PostgreSQL is running
- Check DATABASE_URL, username, and password
- Or comment out JPA in `pom.xml` for testing without database

## Next Steps

1. **Get Shopify Credentials** → See [SHOPIFY_CONNECTION.md](./SHOPIFY_CONNECTION.md)
2. **Test API Endpoints** → See [API_REFERENCE.md](./API_REFERENCE.md)
3. **Deploy to Railway** → See [RAILWAY_DEPLOYMENT.md](./RAILWAY_DEPLOYMENT.md)

## Project Configuration Files

### `pom.xml` - Maven Dependencies

Key dependencies:
- `spring-boot-starter-web` - REST API framework
- `spring-boot-starter-webflux` - WebClient for GraphQL
- `spring-boot-starter-data-jpa` - Database access
- `postgresql` - PostgreSQL driver
- `lombok` - Reduces boilerplate code

### `application.yml` - Application Configuration

- Database settings
- Shopify API configuration
- Rate limiting parameters
- Logging configuration

## Development Workflow

1. **Make code changes**
2. **Build:** `mvn clean install`
3. **Run:** `mvn spring-boot:run`
4. **Test:** Use curl or Postman
5. **Commit:** `git add . && git commit -m "message"`
6. **Push:** `git push origin main`
7. **Deploy:** Automatic on Railway (if connected)

## Getting Help

- Check [TROUBLESHOOTING.md](./TROUBLESHOOTING.md)
- Review Spring Boot docs: https://spring.io/projects/spring-boot
- Shopify API docs: https://shopify.dev/docs/api
