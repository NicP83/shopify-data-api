# Database Schema Design
## AI-Enhanced Search for Hearn's Hobbies

---

## Overview

This document details the database schema changes required to support the Shopify AI-enhanced search integration. The primary addition is the `shopify_shops` table, which stores OAuth tokens, shop configuration, and AI settings per shop.

---

## New Tables

### `shopify_shops` Table

**Purpose:** Store Shopify shop data, OAuth tokens, and AI configuration per shop.

**Migration File:** `V006__create_shopify_shops.sql`

```sql
-- Migration: V006__create_shopify_shops.sql
-- Description: Create shopify_shops table for OAuth and shop-specific AI configuration

CREATE TABLE shopify_shops (
    id BIGSERIAL PRIMARY KEY,

    -- Shop identification
    shop_domain VARCHAR(255) NOT NULL UNIQUE,  -- e.g., "hearnshobbies.myshopify.com"
    shop_name VARCHAR(255),                     -- e.g., "Hearn's Hobbies"
    shop_email VARCHAR(255),                    -- Shop owner email
    shop_owner VARCHAR(255),                    -- Shop owner name

    -- OAuth tokens (encrypted at rest in production)
    access_token TEXT NOT NULL,                 -- Shopify OAuth access token
    scope TEXT,                                 -- OAuth scopes granted

    -- Shop metadata
    plan_name VARCHAR(100),                     -- Shopify plan (e.g., "basic", "shopify", "advanced")
    currency VARCHAR(10) DEFAULT 'USD',         -- Shop currency
    timezone VARCHAR(100),                      -- Shop timezone (e.g., "America/New_York")

    -- AI configuration
    ai_enabled BOOLEAN DEFAULT true,            -- Enable/disable AI search for this shop
    ai_model VARCHAR(100) DEFAULT 'claude-sonnet-4-5-20250929',
    ai_temperature DECIMAL(3,2) DEFAULT 0.7,
    ai_max_tokens INTEGER DEFAULT 4096,
    ai_system_prompt TEXT,                      -- Shop-specific system prompt override

    -- Analytics settings
    analytics_enabled BOOLEAN DEFAULT true,
    track_chat_usage BOOLEAN DEFAULT true,

    -- Installation tracking
    installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uninstalled_at TIMESTAMP,                   -- NULL if currently installed
    is_active BOOLEAN DEFAULT true,             -- Shop actively using the app

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_shopify_shops_domain ON shopify_shops(shop_domain);
CREATE INDEX idx_shopify_shops_active ON shopify_shops(is_active) WHERE is_active = true;
CREATE INDEX idx_shopify_shops_installed ON shopify_shops(installed_at);

-- Updated timestamp trigger
CREATE OR REPLACE FUNCTION update_shopify_shops_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_shopify_shops_updated_at
    BEFORE UPDATE ON shopify_shops
    FOR EACH ROW
    EXECUTE FUNCTION update_shopify_shops_updated_at();

-- Comments for documentation
COMMENT ON TABLE shopify_shops IS 'Stores Shopify shop OAuth tokens and AI configuration';
COMMENT ON COLUMN shopify_shops.shop_domain IS 'Unique Shopify domain (e.g., store.myshopify.com)';
COMMENT ON COLUMN shopify_shops.access_token IS 'OAuth access token for Shopify API (encrypt in production)';
COMMENT ON COLUMN shopify_shops.ai_system_prompt IS 'Shop-specific system prompt override (NULL uses default)';
COMMENT ON COLUMN shopify_shops.uninstalled_at IS 'Timestamp when app was uninstalled (NULL if active)';
```

---

## Existing Tables (No Changes)

The following existing tables remain unchanged but will be referenced:

### `agents` Table
- Stores AI agent configurations
- Used by admin product search assistant
- No modifications needed

### `agent_tools` Table
- Links agents to tools
- Product search tool already exists
- No modifications needed

### `tools` Table
- Stores tool definitions
- `search_products` tool already exists
- No modifications needed

### `chat_sessions` Table (If Exists)
- Stores chat conversation history
- May need shop_id foreign key in future enhancement
- No immediate modifications needed

---

## Entity-Relationship Diagram

```
┌─────────────────────────────────────────┐
│          shopify_shops                  │
├─────────────────────────────────────────┤
│ PK  id                    BIGSERIAL     │
│ UQ  shop_domain          VARCHAR(255)   │
│     shop_name            VARCHAR(255)   │
│     shop_email           VARCHAR(255)   │
│     access_token         TEXT           │
│     ai_enabled           BOOLEAN        │
│     ai_model             VARCHAR(100)   │
│     ai_temperature       DECIMAL(3,2)   │
│     ai_max_tokens        INTEGER        │
│     ai_system_prompt     TEXT           │
│     installed_at         TIMESTAMP      │
│     is_active            BOOLEAN        │
│     created_at           TIMESTAMP      │
│     updated_at           TIMESTAMP      │
└─────────────────────────────────────────┘
          │
          │ (Future enhancement)
          │
          ▼
┌─────────────────────────────────────────┐
│          chat_sessions                  │  (Future: Add shop_id FK)
├─────────────────────────────────────────┤
│ PK  id                    BIGSERIAL     │
│ FK  shop_id              BIGINT         │  ← Future addition
│     session_id           VARCHAR(255)   │
│     messages             JSONB          │
│     created_at           TIMESTAMP      │
└─────────────────────────────────────────┘
```

**Note:** Chat sessions are currently shop-agnostic. Future enhancement could add `shop_id` foreign key to track per-shop conversations.

---

## Sample Data

### Example Shop Record

```sql
INSERT INTO shopify_shops (
    shop_domain,
    shop_name,
    shop_email,
    shop_owner,
    access_token,
    scope,
    plan_name,
    currency,
    timezone,
    ai_enabled,
    ai_model,
    ai_temperature,
    ai_max_tokens,
    ai_system_prompt,
    analytics_enabled,
    installed_at
) VALUES (
    'hearnshobbies.myshopify.com',
    'Hearn''s Hobbies',
    'info@hearnshobbies.com',
    'Store Owner',
    'shpat_abc123def456...', -- OAuth token
    'read_products,read_orders,write_script_tags',
    'shopify',
    'USD',
    'America/New_York',
    true,
    'claude-sonnet-4-5-20250929',
    0.7,
    4096,
    NULL, -- Use default system prompt
    true,
    CURRENT_TIMESTAMP
);
```

### Query Examples

**Get active shop configuration:**
```sql
SELECT
    shop_domain,
    shop_name,
    ai_model,
    ai_temperature,
    ai_max_tokens,
    ai_enabled,
    installed_at
FROM shopify_shops
WHERE shop_domain = 'hearnshobbies.myshopify.com'
  AND is_active = true;
```

**Get all active shops:**
```sql
SELECT
    shop_domain,
    shop_name,
    plan_name,
    installed_at,
    ai_enabled
FROM shopify_shops
WHERE is_active = true
  AND uninstalled_at IS NULL
ORDER BY installed_at DESC;
```

**Update AI configuration for a shop:**
```sql
UPDATE shopify_shops
SET
    ai_model = 'claude-opus-4-1-20250805',
    ai_temperature = 0.8,
    ai_max_tokens = 8192,
    updated_at = CURRENT_TIMESTAMP
WHERE shop_domain = 'hearnshobbies.myshopify.com';
```

**Mark shop as uninstalled:**
```sql
UPDATE shopify_shops
SET
    is_active = false,
    uninstalled_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE shop_domain = 'hearnshobbies.myshopify.com';
```

---

## Security Considerations

### Access Token Encryption

**Development:** Tokens stored as plain text (for testing only)

**Production:** Encrypt `access_token` column at rest using:

1. **Database-level encryption:**
   ```sql
   -- PostgreSQL pgcrypto extension
   CREATE EXTENSION IF NOT EXISTS pgcrypto;

   -- Encrypt token on insert
   INSERT INTO shopify_shops (shop_domain, access_token)
   VALUES (
       'store.myshopify.com',
       pgp_sym_encrypt('shpat_token', 'encryption_key')
   );

   -- Decrypt token on select
   SELECT
       shop_domain,
       pgp_sym_decrypt(access_token::bytea, 'encryption_key') AS decrypted_token
   FROM shopify_shops;
   ```

2. **Application-level encryption (Recommended):**
   - Use Spring Boot's `@ColumnTransformer` with AES-256 encryption
   - Store encryption key in environment variable (not in code)
   - Example implementation:

   ```java
   @Entity
   @Table(name = "shopify_shops")
   public class ShopifyShop {

       @Column(name = "access_token")
       @Convert(converter = EncryptedStringConverter.class)
       private String accessToken;

       // EncryptedStringConverter uses AES-256
   }
   ```

### Access Control

- **Never expose access tokens in API responses**
- **Validate shop domain on every request**
- **Use HMAC signature verification for Shopify webhooks**
- **Rotate tokens on uninstall/reinstall**

---

## Migration Checklist

### Before Running Migration

- [ ] Backup database
- [ ] Test migration on development database first
- [ ] Verify PostgreSQL version (>= 12.0 recommended)
- [ ] Check available disk space (minimal impact)

### Running Migration

```bash
# Using Flyway (Spring Boot default)
mvn flyway:migrate

# Or Spring Boot will run automatically on startup
mvn spring-boot:run
```

### After Migration

- [ ] Verify table exists: `\d shopify_shops` (PostgreSQL)
- [ ] Check indexes created: `\di` (PostgreSQL)
- [ ] Test insert/select operations
- [ ] Verify trigger works (update a record, check `updated_at`)

---

## Rollback Plan

If migration needs to be rolled back:

```sql
-- Rollback script (create as needed)
DROP TRIGGER IF EXISTS trigger_shopify_shops_updated_at ON shopify_shops;
DROP FUNCTION IF EXISTS update_shopify_shops_updated_at();
DROP INDEX IF EXISTS idx_shopify_shops_domain;
DROP INDEX IF EXISTS idx_shopify_shops_active;
DROP INDEX IF EXISTS idx_shopify_shops_installed;
DROP TABLE IF EXISTS shopify_shops;
```

**Note:** Flyway does not support automatic rollbacks. Create manual rollback scripts if needed.

---

## Performance Optimization

### Index Strategy

1. **`idx_shopify_shops_domain`** - Primary lookup index (used on every request)
2. **`idx_shopify_shops_active`** - Partial index for active shops only
3. **`idx_shopify_shops_installed`** - For analytics queries by install date

### Query Optimization

- **Use shop_domain for lookups** (indexed, unique)
- **Avoid SELECT *** - specify columns needed
- **Cache shop configuration** in application layer (reduce DB queries)

### Caching Strategy (Future Enhancement)

```java
// Spring Boot cache configuration
@Cacheable(value = "shopify_shops", key = "#shopDomain")
public ShopifyShop getShop(String shopDomain) {
    return shopifyShopRepository.findByShopDomain(shopDomain)
        .orElseThrow(() -> new ShopNotFoundException(shopDomain));
}

// Evict cache on update
@CacheEvict(value = "shopify_shops", key = "#shop.shopDomain")
public ShopifyShop updateShop(ShopifyShop shop) {
    return shopifyShopRepository.save(shop);
}
```

---

## Future Enhancements

### Potential Schema Additions

1. **Multi-shop support:**
   - Add `shop_group_id` for managing multiple stores
   - Parent-child shop relationships

2. **Analytics tracking:**
   - New table: `shopify_analytics` (chat usage, conversion tracking)
   - Foreign key to `shopify_shops`

3. **Custom system prompts:**
   - New table: `shopify_custom_prompts` (versioned prompts per shop)

4. **Rate limiting:**
   - New table: `shopify_rate_limits` (per-shop API quotas)

5. **Webhook events:**
   - New table: `shopify_webhook_events` (track webhook deliveries)

---

## Testing Queries

### Test Data Setup

```sql
-- Insert test shop
INSERT INTO shopify_shops (
    shop_domain, shop_name, access_token, ai_enabled
) VALUES (
    'test-shop.myshopify.com',
    'Test Shop',
    'test_token_123',
    true
);

-- Verify insert
SELECT * FROM shopify_shops WHERE shop_domain = 'test-shop.myshopify.com';

-- Test update trigger
UPDATE shopify_shops
SET ai_model = 'claude-opus-4-1-20250805'
WHERE shop_domain = 'test-shop.myshopify.com';

-- Verify updated_at changed
SELECT shop_domain, ai_model, created_at, updated_at
FROM shopify_shops
WHERE shop_domain = 'test-shop.myshopify.com';

-- Cleanup
DELETE FROM shopify_shops WHERE shop_domain = 'test-shop.myshopify.com';
```

---

## Related Documentation

- **01-ARCHITECTURE.md** - System architecture and data flow
- **04-PHASE1-BACKEND.md** - Backend implementation guide
- **10-CONFIGURATION.md** - Environment variables and settings

---

*Last Updated: 2025-10-30*
*Next: 03-API-SPECIFICATIONS.md*
