-- =====================================================
-- Inventory Analysis Module - Database Schema
-- Version: 1.0.0
-- Created: 2025-11-06
-- Description: Tables for inventory analysis, sales velocity tracking,
--              order recommendations, and stock alerts
-- =====================================================

-- =====================================================
-- Table: sales_velocity
-- Purpose: Track calculated sales velocity for products
-- =====================================================
CREATE TABLE sales_velocity (
    id BIGSERIAL PRIMARY KEY,

    -- Product identification
    sku VARCHAR(255) NOT NULL UNIQUE,
    product_id VARCHAR(255), -- Shopify product ID for cross-reference

    -- Velocity metrics (units sold per time period)
    daily_average DECIMAL(10,2) NOT NULL,
    weekly_average DECIMAL(10,2) NOT NULL,
    monthly_average DECIMAL(10,2) NOT NULL,

    -- Trend analysis
    trend VARCHAR(50) NOT NULL CHECK (trend IN ('INCREASING', 'DECREASING', 'STABLE')),
    trend_percentage DECIMAL(5,2), -- Positive or negative percentage change

    -- Calculation metadata
    last_calculated TIMESTAMP NOT NULL DEFAULT NOW(),
    calculation_period_days INT NOT NULL, -- e.g., 30, 60, 90
    data_sample_size INT, -- Number of sales transactions analyzed

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for sales_velocity
CREATE INDEX idx_sales_velocity_sku ON sales_velocity(sku);
CREATE INDEX idx_sales_velocity_last_calculated ON sales_velocity(last_calculated);
CREATE INDEX idx_sales_velocity_trend ON sales_velocity(trend);

-- Comment on table
COMMENT ON TABLE sales_velocity IS 'Tracks calculated sales velocity for products over different time periods';

-- =====================================================
-- Table: order_recommendations
-- Purpose: Store AI-generated order recommendations
-- =====================================================
CREATE TABLE order_recommendations (
    id BIGSERIAL PRIMARY KEY,

    -- Product identification
    sku VARCHAR(255) NOT NULL,
    product_title VARCHAR(500),

    -- Stock information
    current_stock INT NOT NULL,
    erp_stock INT, -- Stock level from ERP
    shopify_stock INT, -- Stock level from Shopify (for cross-reference)

    -- Recommendation details
    recommended_quantity INT NOT NULL,
    reorder_point INT NOT NULL,
    safety_stock_level INT,

    -- Supplier information
    lead_time_days INT NOT NULL,
    supplier_name VARCHAR(255),
    cost_per_unit DECIMAL(10,2),
    total_cost DECIMAL(10,2), -- recommendedQuantity × costPerUnit

    -- Urgency and confidence
    urgency VARCHAR(50) NOT NULL CHECK (urgency IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    confidence_score DECIMAL(3,2), -- 0.00 to 1.00

    -- AI-generated insights
    ai_reasoning TEXT, -- Claude's detailed analysis
    recommendations TEXT, -- Specific action items

    -- Predictions
    estimated_stockout_date DATE,
    days_until_stockout INT,

    -- Status tracking
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'ORDERED', 'DISMISSED')),

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    approved_at TIMESTAMP,
    approved_by VARCHAR(255),

    -- Foreign key to velocity calculation
    velocity_id BIGINT REFERENCES sales_velocity(id) ON DELETE SET NULL
);

-- Indexes for order_recommendations
CREATE INDEX idx_order_recommendations_sku ON order_recommendations(sku);
CREATE INDEX idx_order_recommendations_status ON order_recommendations(status);
CREATE INDEX idx_order_recommendations_urgency ON order_recommendations(urgency);
CREATE INDEX idx_order_recommendations_created_at ON order_recommendations(created_at DESC);
CREATE INDEX idx_order_recommendations_stockout_date ON order_recommendations(estimated_stockout_date);
CREATE INDEX idx_order_recommendations_velocity_id ON order_recommendations(velocity_id);

-- Comment on table
COMMENT ON TABLE order_recommendations IS 'AI-generated order recommendations with detailed analysis and urgency levels';

-- =====================================================
-- Table: stock_alerts
-- Purpose: Track low stock alerts and warnings
-- =====================================================
CREATE TABLE stock_alerts (
    id BIGSERIAL PRIMARY KEY,

    -- Product identification
    sku VARCHAR(255) NOT NULL,
    product_title VARCHAR(500),

    -- Stock levels
    current_stock INT NOT NULL,
    reorder_point INT NOT NULL,

    -- Alert details
    alert_level VARCHAR(50) NOT NULL CHECK (alert_level IN ('CRITICAL', 'WARNING', 'INFO')),
    days_until_stockout INT,
    estimated_stockout_date DATE,
    message TEXT, -- Human-readable alert message

    -- Tracking
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,

    -- Foreign key to recommendation
    recommendation_id BIGINT REFERENCES order_recommendations(id) ON DELETE SET NULL
);

-- Indexes for stock_alerts
CREATE INDEX idx_stock_alerts_sku ON stock_alerts(sku);
CREATE INDEX idx_stock_alerts_resolved ON stock_alerts(resolved);
CREATE INDEX idx_stock_alerts_alert_level ON stock_alerts(alert_level);
CREATE INDEX idx_stock_alerts_created_at ON stock_alerts(created_at DESC);
CREATE INDEX idx_stock_alerts_stockout_date ON stock_alerts(estimated_stockout_date);
CREATE INDEX idx_stock_alerts_recommendation_id ON stock_alerts(recommendation_id);

-- Composite index for active critical alerts
CREATE INDEX idx_stock_alerts_active_critical ON stock_alerts(resolved, alert_level, created_at DESC)
    WHERE resolved = FALSE AND alert_level = 'CRITICAL';

-- Comment on table
COMMENT ON TABLE stock_alerts IS 'Tracks low stock alerts with severity levels and resolution status';

-- =====================================================
-- Table: analysis_history (Optional - for audit trail)
-- Purpose: Track analysis execution history
-- =====================================================
CREATE TABLE analysis_history (
    id BIGSERIAL PRIMARY KEY,

    -- Analysis type
    analysis_type VARCHAR(50) NOT NULL, -- 'FULL_SCAN', 'SINGLE_PRODUCT', 'SCHEDULED'

    -- Results
    products_analyzed INT,
    recommendations_generated INT,
    alerts_created INT,

    -- Performance
    execution_time_ms BIGINT,
    success BOOLEAN NOT NULL,
    error_message TEXT,

    -- Timestamp
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for analysis_history
CREATE INDEX idx_analysis_history_created_at ON analysis_history(created_at DESC);
CREATE INDEX idx_analysis_history_type ON analysis_history(analysis_type);
CREATE INDEX idx_analysis_history_success ON analysis_history(success);

-- Comment on table
COMMENT ON TABLE analysis_history IS 'Audit trail of inventory analysis executions for monitoring and debugging';

-- =====================================================
-- Views for easier querying
-- =====================================================

-- View: Current critical alerts
CREATE OR REPLACE VIEW v_critical_alerts AS
SELECT
    sa.id,
    sa.sku,
    sa.product_title,
    sa.current_stock,
    sa.reorder_point,
    sa.days_until_stockout,
    sa.estimated_stockout_date,
    sa.message,
    sa.created_at,
    or_rec.recommended_quantity,
    or_rec.urgency
FROM stock_alerts sa
LEFT JOIN order_recommendations or_rec ON sa.recommendation_id = or_rec.id
WHERE sa.resolved = FALSE
  AND sa.alert_level = 'CRITICAL'
ORDER BY sa.days_until_stockout ASC NULLS LAST, sa.created_at DESC;

COMMENT ON VIEW v_critical_alerts IS 'Current critical stock alerts requiring immediate attention';

-- View: Pending recommendations summary
CREATE OR REPLACE VIEW v_pending_recommendations AS
SELECT
    or_rec.id,
    or_rec.sku,
    or_rec.product_title,
    or_rec.current_stock,
    or_rec.recommended_quantity,
    or_rec.urgency,
    or_rec.total_cost,
    or_rec.days_until_stockout,
    or_rec.estimated_stockout_date,
    or_rec.created_at,
    sv.daily_average as sales_velocity_daily,
    sv.trend
FROM order_recommendations or_rec
LEFT JOIN sales_velocity sv ON or_rec.velocity_id = sv.id
WHERE or_rec.status = 'PENDING'
ORDER BY
    CASE or_rec.urgency
        WHEN 'CRITICAL' THEN 1
        WHEN 'HIGH' THEN 2
        WHEN 'MEDIUM' THEN 3
        WHEN 'LOW' THEN 4
    END,
    or_rec.days_until_stockout ASC NULLS LAST;

COMMENT ON VIEW v_pending_recommendations IS 'All pending order recommendations sorted by urgency';

-- View: Inventory health summary
CREATE OR REPLACE VIEW v_inventory_health_summary AS
SELECT
    COUNT(DISTINCT sv.sku) as total_tracked_products,
    COUNT(DISTINCT CASE WHEN sa.alert_level = 'CRITICAL' AND sa.resolved = FALSE THEN sa.sku END) as critical_count,
    COUNT(DISTINCT CASE WHEN sa.alert_level = 'WARNING' AND sa.resolved = FALSE THEN sa.sku END) as warning_count,
    COUNT(DISTINCT CASE WHEN or_rec.status = 'PENDING' THEN or_rec.sku END) as pending_recommendations,
    COALESCE(SUM(CASE WHEN or_rec.status = 'PENDING' THEN or_rec.total_cost ELSE 0 END), 0) as total_pending_order_value
FROM sales_velocity sv
LEFT JOIN stock_alerts sa ON sv.sku = sa.sku AND sa.resolved = FALSE
LEFT JOIN order_recommendations or_rec ON sv.sku = or_rec.sku AND or_rec.status = 'PENDING';

COMMENT ON VIEW v_inventory_health_summary IS 'High-level summary of inventory health metrics';

-- =====================================================
-- Functions for data maintenance
-- =====================================================

-- Function: Resolve old alerts automatically
CREATE OR REPLACE FUNCTION resolve_old_alerts()
RETURNS void AS $$
BEGIN
    -- Resolve alerts older than 30 days that are still unresolved
    UPDATE stock_alerts
    SET resolved = TRUE,
        resolved_at = NOW()
    WHERE resolved = FALSE
      AND created_at < NOW() - INTERVAL '30 days';
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION resolve_old_alerts() IS 'Automatically resolve stock alerts older than 30 days';

-- Function: Clean old analysis history
CREATE OR REPLACE FUNCTION clean_old_analysis_history(retention_days INT DEFAULT 90)
RETURNS INT AS $$
DECLARE
    deleted_count INT;
BEGIN
    DELETE FROM analysis_history
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION clean_old_analysis_history(INT) IS 'Delete analysis history older than specified days (default 90)';

-- =====================================================
-- Grants (adjust based on your user setup)
-- =====================================================

-- Grant permissions to application user if needed
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO your_app_user;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO your_app_user;
-- GRANT SELECT ON ALL TABLES IN SCHEMA public TO your_readonly_user;

-- =====================================================
-- Initial data / seed data (optional)
-- =====================================================

-- No initial data required - tables will be populated by the application

-- =====================================================
-- Verification queries (commented out)
-- =====================================================

-- Verify tables created:
-- SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE '%velocity%' OR table_name LIKE '%recommendation%' OR table_name LIKE '%alert%';

-- Verify indexes:
-- SELECT tablename, indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename IN ('sales_velocity', 'order_recommendations', 'stock_alerts');

-- Verify views:
-- SELECT table_name FROM information_schema.views WHERE table_schema = 'public' AND table_name LIKE 'v_%';

-- =====================================================
-- Migration complete
-- =====================================================
