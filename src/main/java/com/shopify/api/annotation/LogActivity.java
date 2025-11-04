package com.shopify.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that should be logged for audit trail.
 * When applied to a method, the ActivityLoggingAspect will automatically
 * create an admin activity log entry.
 *
 * Example usage:
 * <pre>
 * {@code
 * @LogActivity(
 *     actionType = "UPDATE",
 *     actionCategory = "CONFIG",
 *     resourceType = "ai_model"
 * )
 * public void updateAIModel(ShopifyShop shop, AIModelConfig config) {
 *     // method implementation
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogActivity {

    /**
     * The type of action being performed
     * Valid values: CREATE, UPDATE, DELETE, TEST, ROLLBACK, VIEW
     */
    String actionType();

    /**
     * The category of the action
     * Valid values: CONFIG, PROMPT, TESTING, ANALYTICS, SYSTEM
     */
    String actionCategory();

    /**
     * The type of resource being acted upon
     * Examples: system_prompt, shop_config, ai_model, test_result
     */
    String resourceType();

    /**
     * Optional description template for the change summary
     * Supports placeholders: {0}, {1}, {2} for method parameters
     * Example: "Updated {0} configuration for shop {1}"
     */
    String descriptionTemplate() default "";

    /**
     * Whether to capture the method's return value as the new value
     * Useful for CREATE and UPDATE operations
     */
    boolean captureReturnValue() default false;

    /**
     * Whether to capture method parameters as context
     * Parameters will be stored in the metadata field
     */
    boolean captureParameters() default false;

    /**
     * Parameter index for the shop object (to associate log with shop)
     * Default is 0 (first parameter assumed to be ShopifyShop)
     * Set to -1 to disable shop association
     */
    int shopParameterIndex() default 0;

    /**
     * Parameter index for the user email
     * Default is -1 (will try to extract from security context)
     * Set to specific index if user email is passed as parameter
     */
    int userEmailParameterIndex() default -1;

    /**
     * Parameter index for the resource ID
     * Default is -1 (will try to extract from return value or parameters)
     */
    int resourceIdParameterIndex() default -1;
}
