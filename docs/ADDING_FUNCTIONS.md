# Adding New Functions Guide

This guide shows you how to add new API endpoints to extend the functionality of your Shopify Data API.

## Overview

Adding a new function follows this pattern:

```
GraphQL Query → Service Layer → Controller → REST Endpoint
```

**Time estimate:** ~30 minutes per function after initial setup

## Architecture Pattern

Every new function requires 3 files:

1. **Service** (`src/main/java/com/shopify/api/service/`) - Business logic
2. **Controller** (`src/main/java/com/shopify/api/controller/`) - REST endpoint
3. **Documentation** (update API_REFERENCE.md)

## Step-by-Step Example

Let's add a new function: **Get Order Fulfillments**

### Step 1: Create the GraphQL Query (Service)

**File:** `src/main/java/com/shopify/api/service/OrderService.java`

Add a new method:

```java
/**
 * Get fulfillments for an order
 * @param orderId The Shopify order ID
 * @return Fulfillment data
 */
public Map<String, Object> getOrderFulfillments(String orderId) {
    logger.info("Fetching fulfillments for order: {}", orderId);

    String query = String.format("""
        {
          order(id: "%s") {
            id
            name
            fulfillments(first: 50) {
              id
              status
              createdAt
              updatedAt
              trackingCompany
              trackingNumbers
              trackingUrls
              lineItems(first: 50) {
                edges {
                  node {
                    id
                    title
                    quantity
                  }
                }
              }
            }
          }
        }
        """, orderId);

    GraphQLResponse response = graphQLClient.executeQuery(query);

    if (response.hasErrors()) {
        logger.error("Error fetching fulfillments: {}", response.getErrors());
        throw new RuntimeException("Failed to fetch fulfillments: " +
                response.getErrors().get(0).getMessage());
    }

    return response.getData();
}
```

### Step 2: Create the REST Endpoint (Controller)

**File:** `src/main/java/com/shopify/api/controller/OrderController.java`

Add a new endpoint method:

```java
/**
 * GET /api/orders/{id}/fulfillments
 * Fetch fulfillments for a specific order
 *
 * @param id Shopify order ID
 * @return Fulfillment details
 */
@GetMapping("/{id}/fulfillments")
public ResponseEntity<ApiResponse<Map<String, Object>>> getOrderFulfillments(
        @PathVariable String id) {

    logger.info("GET /api/orders/{}/fulfillments", id);

    try {
        String orderId = id.startsWith("gid://") ? id : "gid://shopify/Order/" + id;
        Map<String, Object> fulfillments = orderService.getOrderFulfillments(orderId);
        return ResponseEntity.ok(ApiResponse.success(fulfillments));
    } catch (Exception e) {
        logger.error("Error fetching fulfillments for order {}", id, e);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to fetch fulfillments", e.getMessage()));
    }
}
```

### Step 3: Test the New Endpoint

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run

# Test the new endpoint
curl "http://localhost:8080/api/orders/987654/fulfillments"
```

### Step 4: Document the Endpoint

Update `docs/API_REFERENCE.md`:

```markdown
### GET /api/orders/{id}/fulfillments

Fetch fulfillment information for an order.

**Request:**
```bash
curl "http://localhost:8080/api/orders/987654/fulfillments"
```

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "order": {
      "id": "gid://shopify/Order/987654",
      "name": "#1001",
      "fulfillments": [
        {
          "id": "gid://shopify/Fulfillment/111",
          "status": "success",
          "trackingCompany": "UPS",
          "trackingNumbers": ["1Z999AA10123456784"],
          "trackingUrls": ["https://..."]
        }
      ]
    }
  }
}
```
```

Done! You've added a new function.

---

## Common Function Templates

### Template 1: List Resources

For fetching a list of items (products, orders, etc.):

**Service Method:**
```java
public Map<String, Object> getResources(int first) {
    String query = String.format("""
        {
          resources(first: %d) {
            edges {
              node {
                id
                field1
                field2
              }
            }
            pageInfo {
              hasNextPage
              hasPreviousPage
            }
          }
        }
        """, Math.min(first, 250));

    GraphQLResponse response = graphQLClient.executeQuery(query);
    // Error handling...
    return response.getData();
}
```

**Controller Method:**
```java
@GetMapping
public ResponseEntity<ApiResponse<Map<String, Object>>> getResources(
        @RequestParam(defaultValue = "50") int first) {
    try {
        Map<String, Object> data = resourceService.getResources(first);
        return ResponseEntity.ok(ApiResponse.success(data));
    } catch (Exception e) {
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to fetch resources", e.getMessage()));
    }
}
```

### Template 2: Get Single Resource

For fetching one item by ID:

**Service Method:**
```java
public Map<String, Object> getResourceById(String resourceId) {
    String query = String.format("""
        {
          resource(id: "%s") {
            id
            field1
            field2
            relatedField {
              id
              name
            }
          }
        }
        """, resourceId);

    GraphQLResponse response = graphQLClient.executeQuery(query);
    // Error handling...
    return response.getData();
}
```

**Controller Method:**
```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<Map<String, Object>>> getResourceById(
        @PathVariable String id) {
    try {
        String resourceId = id.startsWith("gid://") ?
            id : "gid://shopify/Resource/" + id;
        Map<String, Object> data = resourceService.getResourceById(resourceId);
        return ResponseEntity.ok(ApiResponse.success(data));
    } catch (Exception e) {
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to fetch resource", e.getMessage()));
    }
}
```

### Template 3: Search Resources

For searching with queries:

**Service Method:**
```java
public Map<String, Object> searchResources(String searchQuery, int first) {
    String query = String.format("""
        {
          resources(first: %d, query: "%s") {
            edges {
              node {
                id
                field1
                field2
              }
            }
          }
        }
        """, Math.min(first, 250), searchQuery);

    GraphQLResponse response = graphQLClient.executeQuery(query);
    // Error handling...
    return response.getData();
}
```

**Controller Method:**
```java
@GetMapping("/search")
public ResponseEntity<ApiResponse<Map<String, Object>>> searchResources(
        @RequestParam String q,
        @RequestParam(defaultValue = "20") int first) {
    try {
        Map<String, Object> results = resourceService.searchResources(q, first);
        return ResponseEntity.ok(ApiResponse.success(results));
    } catch (Exception e) {
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to search resources", e.getMessage()));
    }
}
```

---

## Example Functions to Add

Here are common functions you might want to add:

### 1. Get Product Metafields

**Why:** Access custom data attached to products

**Service:**
```java
public Map<String, Object> getProductMetafields(String productId) {
    String query = String.format("""
        {
          product(id: "%s") {
            id
            title
            metafields(first: 50) {
              edges {
                node {
                  id
                  namespace
                  key
                  value
                  type
                }
              }
            }
          }
        }
        """, productId);
    // ...
}
```

**Endpoint:** `GET /api/products/{id}/metafields`

### 2. Get Order Timeline

**Why:** Track order history and events

**Service:**
```java
public Map<String, Object> getOrderTimeline(String orderId) {
    String query = String.format("""
        {
          order(id: "%s") {
            id
            name
            events(first: 50) {
              id
              message
              createdAt
              attributeToUser
            }
          }
        }
        """, orderId);
    // ...
}
```

**Endpoint:** `GET /api/orders/{id}/timeline`

### 3. Get Collections

**Why:** Access product collections/categories

**Service:**
```java
public Map<String, Object> getCollections(int first) {
    String query = String.format("""
        {
          collections(first: %d) {
            edges {
              node {
                id
                title
                handle
                description
                productsCount
                image {
                  url
                }
              }
            }
          }
        }
        """, Math.min(first, 250));
    // ...
}
```

**Endpoint:** `GET /api/collections`

### 4. Get Shop Information

**Why:** Access store details

**Service:**
```java
public Map<String, Object> getShopInfo() {
    String query = """
        {
          shop {
            id
            name
            email
            currencyCode
            primaryDomain {
              url
            }
            plan {
              displayName
            }
          }
        }
        """;
    // ...
}
```

**Endpoint:** `GET /api/shop`

### 5. Get Customer Orders

**Why:** Fetch all orders for a specific customer

**Service:**
```java
public Map<String, Object> getCustomerOrders(String customerId, int first) {
    String query = String.format("""
        {
          customer(id: "%s") {
            id
            email
            orders(first: %d) {
              edges {
                node {
                  id
                  name
                  createdAt
                  displayFinancialStatus
                  totalPrice
                }
              }
            }
          }
        }
        """, customerId, Math.min(first, 250));
    // ...
}
```

**Endpoint:** `GET /api/customers/{id}/orders`

---

## Testing New Functions

### 1. Unit Test (Optional but Recommended)

Create test file: `src/test/java/com/shopify/api/service/ResourceServiceTest.java`

```java
@SpringBootTest
class ResourceServiceTest {

    @Autowired
    private ResourceService resourceService;

    @Test
    void testGetResource() {
        Map<String, Object> result = resourceService.getResource("123");
        assertNotNull(result);
    }
}
```

### 2. Integration Test with curl

```bash
# Test the endpoint
curl "http://localhost:8080/api/your-new-endpoint"

# Test with parameters
curl "http://localhost:8080/api/your-new-endpoint?param=value"

# Test error handling
curl "http://localhost:8080/api/your-new-endpoint/invalid-id"
```

### 3. Postman Collection

Add the new endpoint to your Postman collection:
1. Create new request
2. Add URL and parameters
3. Test and save examples

---

## Best Practices

### 1. Always Include Error Handling

```java
try {
    // Your logic
    return ResponseEntity.ok(ApiResponse.success(data));
} catch (Exception e) {
    logger.error("Error message", e);
    return ResponseEntity.internalServerError()
            .body(ApiResponse.error("User-friendly message", e.getMessage()));
}
```

### 2. Log Important Events

```java
logger.info("Fetching resource: {}", resourceId);
logger.debug("Query: {}", query);
logger.error("Failed to fetch resource", exception);
```

### 3. Handle Shopify ID Format

```java
// Accept both formats
String resourceId = id.startsWith("gid://") ?
    id : "gid://shopify/Resource/" + id;
```

### 4. Limit Query Results

```java
// Always cap at Shopify's max
int safeLimit = Math.min(first, 250);
```

### 5. Document Your Endpoint

Always update API_REFERENCE.md with:
- Endpoint path
- Parameters
- Example request
- Example response

---

## Shopify GraphQL Resources

### Finding Available Fields

1. **GraphQL Explorer:** https://shopify.dev/docs/apps/tools/graphiql-admin-api
   - Interactive query builder
   - Shows all available fields
   - Auto-completion

2. **API Reference:** https://shopify.dev/docs/api/admin-graphql
   - Complete field documentation
   - Field descriptions
   - Data types

### Common Shopify Objects

- **Product** - Products and variants
- **Order** - Customer orders
- **Customer** - Customer information
- **Collection** - Product collections
- **InventoryItem** - Inventory levels
- **Location** - Physical locations
- **Fulfillment** - Order fulfillments
- **Refund** - Order refunds
- **Discount** - Discount codes
- **Shop** - Store information

---

## Deployment After Adding Functions

### 1. Test Locally

```bash
mvn clean install
mvn spring-boot:run
# Test all endpoints
```

### 2. Commit Changes

```bash
git add .
git commit -m "Add order fulfillments endpoint"
git push origin main
```

### 3. Auto-Deploy on Railway

Railway automatically deploys when you push to main.

Monitor deployment:
1. Go to Railway dashboard
2. Watch build logs
3. Test live endpoint

---

## Troubleshooting New Functions

### GraphQL Query Errors

**Problem:** Query returns errors

**Solutions:**
- Test query in GraphQL Explorer first
- Check field names (case-sensitive)
- Verify API scopes in Shopify

### Null Pointer Exceptions

**Problem:** `NullPointerException` when parsing response

**Solutions:**
- Check if field exists in response
- Handle optional fields with null checks
- Log the raw response for debugging

### Rate Limit Issues

**Problem:** New function hits rate limits

**Solutions:**
- Reduce number of fields requested
- Optimize query depth
- The rate limiter handles this automatically

---

## Quick Reference Checklist

When adding a new function:

- [ ] Write GraphQL query in Service
- [ ] Add error handling
- [ ] Create Controller endpoint
- [ ] Add logging
- [ ] Test locally with curl
- [ ] Update API_REFERENCE.md
- [ ] Commit and push
- [ ] Test on Railway
- [ ] Update any client applications

---

## Need Help?

- Review existing services for examples
- Check Shopify GraphQL docs
- Test queries in GraphQL Explorer
- See TROUBLESHOOTING.md for common issues
