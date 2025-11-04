package com.shopify.api.aspect;

import com.shopify.api.annotation.LogActivity;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.service.AdminActivityLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * AOP Aspect for automatic activity logging.
 * Intercepts methods annotated with @LogActivity and creates audit log entries.
 */
@Aspect
@Component
public class ActivityLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(ActivityLoggingAspect.class);

    @Autowired
    private AdminActivityLogService activityLogService;

    /**
     * Around advice for @LogActivity annotated methods
     * Captures method execution, parameters, return value, and timing
     */
    @Around("@annotation(com.shopify.api.annotation.LogActivity)")
    public Object logActivity(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LogActivity annotation = method.getAnnotation(LogActivity.class);

        // Extract context
        Object[] args = joinPoint.getArgs();
        ShopifyShop shop = extractShop(args, annotation.shopParameterIndex());
        String userEmail = extractUserEmail(args, annotation.userEmailParameterIndex());
        String resourceId = extractResourceId(args, annotation.resourceIdParameterIndex());
        HttpServletRequest request = getCurrentRequest();

        // Prepare metadata
        Map<String, Object> metadata = new HashMap<>();
        if (annotation.captureParameters()) {
            metadata.put("parameters", captureParameters(args, signature.getParameterNames()));
        }

        Object returnValue = null;
        boolean success = true;
        String errorMessage = null;

        try {
            // Execute the actual method
            returnValue = joinPoint.proceed();

            // Extract resource ID from return value if not provided
            if (resourceId == null && returnValue != null) {
                resourceId = extractResourceIdFromReturnValue(returnValue);
            }

            // Capture return value if requested
            if (annotation.captureReturnValue() && returnValue != null) {
                metadata.put("returnValue", sanitizeForLogging(returnValue));
            }

        } catch (Throwable throwable) {
            success = false;
            errorMessage = throwable.getMessage();
            throw throwable; // Re-throw to maintain normal error handling

        } finally {
            // Log the activity asynchronously
            try {
                String changeSummary = buildChangeSummary(
                        annotation.descriptionTemplate(),
                        args,
                        method.getName(),
                        success
                );

                if (success) {
                    activityLogService.logSuccess(
                            shop,
                            userEmail,
                            annotation.actionType(),
                            annotation.actionCategory(),
                            annotation.resourceType(),
                            resourceId,
                            changeSummary,
                            startTime,
                            request
                    );
                } else {
                    activityLogService.logFailure(
                            shop,
                            userEmail,
                            annotation.actionType(),
                            annotation.actionCategory(),
                            annotation.resourceType(),
                            resourceId,
                            errorMessage,
                            request
                    );
                }

            } catch (Exception e) {
                // Don't let logging failures break the main operation
                logger.error("Failed to log activity for method: {}", method.getName(), e);
            }
        }

        return returnValue;
    }

    /**
     * Extract ShopifyShop from method parameters
     */
    private ShopifyShop extractShop(Object[] args, int shopParameterIndex) {
        if (shopParameterIndex < 0 || shopParameterIndex >= args.length) {
            return null;
        }

        Object arg = args[shopParameterIndex];
        if (arg instanceof ShopifyShop) {
            return (ShopifyShop) arg;
        }

        return null;
    }

    /**
     * Extract user email from method parameters or security context
     */
    private String extractUserEmail(Object[] args, int userEmailParameterIndex) {
        // Try to get from parameters first
        if (userEmailParameterIndex >= 0 && userEmailParameterIndex < args.length) {
            Object arg = args[userEmailParameterIndex];
            if (arg instanceof String) {
                return (String) arg;
            }
        }

        // Try to get from request parameter
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            String emailParam = request.getParameter("userEmail");
            if (emailParam != null) {
                return emailParam;
            }

            // Try to get from header
            String emailHeader = request.getHeader("X-User-Email");
            if (emailHeader != null) {
                return emailHeader;
            }
        }

        // Default fallback
        return "system";
    }

    /**
     * Extract resource ID from method parameters
     */
    private String extractResourceId(Object[] args, int resourceIdParameterIndex) {
        if (resourceIdParameterIndex < 0 || resourceIdParameterIndex >= args.length) {
            return null;
        }

        Object arg = args[resourceIdParameterIndex];
        if (arg == null) {
            return null;
        }

        return arg.toString();
    }

    /**
     * Extract resource ID from return value (for CREATE operations)
     */
    private String extractResourceIdFromReturnValue(Object returnValue) {
        try {
            // Try to get ID via reflection
            Method getIdMethod = returnValue.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(returnValue);
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            // Silently fail if no getId method
            return null;
        }
    }

    /**
     * Capture method parameters as map
     */
    private Map<String, Object> captureParameters(Object[] args, String[] paramNames) {
        Map<String, Object> params = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            String paramName = paramNames != null && i < paramNames.length
                    ? paramNames[i]
                    : "arg" + i;

            Object sanitized = sanitizeForLogging(args[i]);
            params.put(paramName, sanitized);
        }

        return params;
    }

    /**
     * Sanitize objects for logging (remove sensitive data, handle complex objects)
     */
    private Object sanitizeForLogging(Object obj) {
        if (obj == null) {
            return null;
        }

        // Don't log sensitive data
        if (obj.toString().toLowerCase().contains("password")
                || obj.toString().toLowerCase().contains("token")
                || obj.toString().toLowerCase().contains("secret")) {
            return "[REDACTED]";
        }

        // Handle primitive types and strings
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            return obj;
        }

        // For complex objects, just return class name to avoid serialization issues
        return obj.getClass().getSimpleName() + "@" + Integer.toHexString(obj.hashCode());
    }

    /**
     * Build change summary from template and parameters
     */
    private String buildChangeSummary(
            String template,
            Object[] args,
            String methodName,
            boolean success
    ) {
        if (template == null || template.isEmpty()) {
            // Default summary
            return String.format("%s %s",
                    success ? "Successfully executed" : "Failed to execute",
                    methodName
            );
        }

        // Replace placeholders {0}, {1}, {2}, etc. with arguments
        String summary = template;
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{" + i + "}";
            if (summary.contains(placeholder)) {
                Object sanitized = sanitizeForLogging(args[i]);
                summary = summary.replace(placeholder, String.valueOf(sanitized));
            }
        }

        return summary;
    }

    /**
     * Get current HTTP request from thread local
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
