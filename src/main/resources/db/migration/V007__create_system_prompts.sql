-- Create system_prompts table for managing AI assistant prompts
-- Supports shop-specific and global prompts with versioning

CREATE TABLE system_prompts (
    id BIGSERIAL PRIMARY KEY,

    -- Shop association (NULL for global prompts)
    shop_id BIGINT REFERENCES shopify_shops(id) ON DELETE CASCADE,

    -- Prompt identification
    prompt_name VARCHAR(100) NOT NULL,
    prompt_type VARCHAR(50) NOT NULL DEFAULT 'product_search',

    -- Prompt content
    prompt_text TEXT NOT NULL,
    prompt_description TEXT,

    -- Versioning
    version INTEGER NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT true,

    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),

    -- Performance tracking
    usage_count INTEGER DEFAULT 0,
    avg_response_time_ms INTEGER DEFAULT 0,
    success_rate DECIMAL(5,2) DEFAULT 100.00,

    -- Constraints
    CONSTRAINT unique_shop_prompt_version UNIQUE (shop_id, prompt_name, version),
    CONSTRAINT check_prompt_type CHECK (prompt_type IN ('product_search', 'general_chat', 'order_inquiry', 'support', 'custom'))
);

-- Create index for shop-specific prompt queries
CREATE INDEX idx_system_prompts_shop_active ON system_prompts(shop_id, is_active) WHERE is_active = true;

-- Create index for prompt type queries
CREATE INDEX idx_system_prompts_type ON system_prompts(prompt_type);

-- Create index for global prompts
CREATE INDEX idx_system_prompts_global ON system_prompts(prompt_name) WHERE shop_id IS NULL;

-- Add trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_system_prompts_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_system_prompts_updated_at
    BEFORE UPDATE ON system_prompts
    FOR EACH ROW
    EXECUTE FUNCTION update_system_prompts_updated_at();

-- Insert default global product search prompt
INSERT INTO system_prompts (prompt_name, prompt_type, prompt_text, prompt_description, is_active, created_by)
VALUES (
    'default_product_search',
    'product_search',
    'You are an AI shopping assistant for a hobby and model building supply store.

Your role:
- Help customers find the perfect products based on their needs
- Provide detailed product information including specifications, compatibility, and usage tips
- Suggest complementary products to complete their projects
- Answer questions about model building techniques, tools, and materials

Product Categories:
- Model kits (Gundam, scale models, military models, aircraft, vehicles)
- Hobby paints (acrylics, enamels, lacquers, weathering supplies)
- Tools (nippers, knives, files, sanding tools, airbrushes)
- Supplies (glue, putty, decals, masking tape, primers)
- Accessories (display cases, LED kits, photo-etch parts)

Response Guidelines:
1. Always search for products before responding to product-related queries
2. Be specific about brands, scales, and product details when available
3. Provide pricing information when discussing products
4. Suggest alternatives if exact match isn''t found
5. Keep responses friendly, enthusiastic, and informative
6. If customer asks non-product questions, politely redirect to products
7. Use product handles for links, not IDs

Format product recommendations clearly:
- Include product name, brand, price, and key features
- Mention if items are in stock or popular
- Suggest complementary items when relevant

Example Response:
"I found some great Gundam model kits that match your criteria! The RG 1/144 RX-78-2 Gundam ($29.99) is a fantastic Real Grade kit with excellent detail and articulation. For painting, I recommend the Tamiya Acrylic Paint Set ($24.99) which includes essential colors. You''ll also need hobby nippers ($12.99) for clean cuts."

Remember: You''re here to help hobbyists find exactly what they need for their projects!',
    'Default system prompt for product search assistant',
    true,
    'system'
);

-- Insert comments
COMMENT ON TABLE system_prompts IS 'Stores AI system prompts with shop-specific and global support';
COMMENT ON COLUMN system_prompts.shop_id IS 'NULL for global prompts, otherwise shop-specific';
COMMENT ON COLUMN system_prompts.prompt_type IS 'Type of prompt: product_search, general_chat, order_inquiry, support, custom';
COMMENT ON COLUMN system_prompts.version IS 'Version number for prompt history tracking';
COMMENT ON COLUMN system_prompts.usage_count IS 'Number of times this prompt has been used';
COMMENT ON COLUMN system_prompts.success_rate IS 'Percentage of successful interactions using this prompt';
