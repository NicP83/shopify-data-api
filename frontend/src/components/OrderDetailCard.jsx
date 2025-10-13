import PropTypes from 'prop-types'

function OrderDetailCard({ order }) {
  if (!order) return null

  const handlePrint = () => {
    // Create a new window with only this order's content for printing
    const printWindow = window.open('', '_blank', 'width=800,height=600')

    // Build HTML for print window
    const printContent = `
      <!DOCTYPE html>
      <html>
        <head>
          <title>Order ${order.orderName || ''} - Print</title>
          <style>
            body {
              font-family: system-ui, -apple-system, sans-serif;
              padding: 20px;
              max-width: 800px;
              margin: 0 auto;
            }
            h1 { font-size: 24px; margin-bottom: 20px; }
            h2 { font-size: 18px; margin-top: 20px; margin-bottom: 10px; }
            .section {
              margin-bottom: 20px;
              padding: 15px;
              border: 1px solid #e5e7eb;
              border-radius: 8px;
            }
            .customer-info { background-color: #eff6ff; }
            .shipping-address { background-color: #f0fdf4; }
            .order-notes { background-color: #fef3c7; }
            table {
              width: 100%;
              border-collapse: collapse;
              margin-top: 10px;
            }
            th, td {
              padding: 8px;
              text-align: left;
              border-bottom: 1px solid #e5e7eb;
            }
            th {
              background-color: #f9fafb;
              font-weight: 600;
            }
            .text-right { text-align: right; }
            .font-mono { font-family: monospace; }
            @media print {
              body { padding: 0; }
              .no-print { display: none; }
            }
          </style>
        </head>
        <body>
          <h1>Order ${order.orderName || 'N/A'}</h1>

          <div class="section customer-info">
            <h2>Customer Information</h2>
            <p><strong>Name:</strong> ${order.customerName || 'N/A'}</p>
            <p><strong>Email:</strong> ${order.customerEmail || 'N/A'}</p>
            ${order.customerPhone ? `<p><strong>Phone:</strong> ${order.customerPhone}</p>` : ''}
          </div>

          <div class="section">
            <h2>Items to Fulfill</h2>
            <table>
              <thead>
                <tr>
                  <th>SKU</th>
                  <th>Product</th>
                  <th>Variant</th>
                  <th class="text-right">Quantity</th>
                  <th class="text-right">Price</th>
                  <th>Sale/Discount</th>
                </tr>
              </thead>
              <tbody>
                ${order.lineItems && order.lineItems.length > 0 ? order.lineItems.map(item => `
                  <tr>
                    <td class="font-mono">${item.sku || 'N/A'}</td>
                    <td>${item.title || ''}</td>
                    <td>${item.variantTitle || '-'}</td>
                    <td class="text-right">${item.quantity || 0}</td>
                    <td class="text-right">$${parseFloat(item.price || 0).toFixed(2)}</td>
                    <td>
                      ${item.onSale ? `<span>🏷️ On Sale in CRS ${item.crsSalePrice ? `($${parseFloat(item.crsSalePrice).toFixed(2)})` : ''}</span><br>` : ''}
                      ${item.discountAllocations ? `<span>💰 ${item.discountAllocations}</span>` : ''}
                      ${!item.onSale && !item.discountAllocations ? '-' : ''}
                    </td>
                  </tr>
                `).join('') : '<tr><td colspan="6">No items found</td></tr>'}
              </tbody>
            </table>
            <p style="margin-top: 10px;"><strong>Total Items:</strong> ${order.itemCount || 0}</p>
            ${order.totalDiscounts && parseFloat(order.totalDiscounts) > 0 ? `
              <p style="margin-top: 10px;">
                <strong>Order Discounts:</strong> -$${parseFloat(order.totalDiscounts).toFixed(2)}
                ${order.discountCodes ? ` (${order.discountCodes})` : ''}
              </p>
              ${order.subtotalPrice ? `
                <p style="font-size: 14px; color: #666;">
                  Subtotal: $${parseFloat(order.subtotalPrice).toFixed(2)} →
                  Final: $${parseFloat(order.totalPrice).toFixed(2)}
                </p>
              ` : ''}
            ` : ''}
          </div>

          ${order.shippingAddress ? `
            <div class="section shipping-address">
              <h2>Shipping Address</h2>
              <pre style="white-space: pre-line; font-family: inherit; margin: 0;">${order.shippingAddress}</pre>
            </div>
          ` : ''}

          ${order.note ? `
            <div class="section order-notes">
              <h2>Order Notes</h2>
              <p>${order.note}</p>
            </div>
          ` : ''}

          <div class="no-print" style="margin-top: 30px; text-align: center;">
            <button onclick="window.print()" style="padding: 10px 20px; font-size: 16px; cursor: pointer;">Print</button>
            <button onclick="window.close()" style="padding: 10px 20px; font-size: 16px; cursor: pointer; margin-left: 10px;">Close</button>
          </div>
        </body>
      </html>
    `

    printWindow.document.write(printContent)
    printWindow.document.close()

    // Auto-print after content loads
    printWindow.onload = () => {
      printWindow.print()
    }
  }

  return (
    <div className="space-y-4">
      {/* Print Button */}
      <div className="flex justify-end print:hidden">
        <button
          onClick={handlePrint}
          className="btn-primary flex items-center gap-2"
        >
          <span>🖨️</span>
          Print Order
        </button>
      </div>
      {/* Customer Info */}
      <div className="card bg-blue-50 border-blue-200">
        <h3 className="font-semibold text-gray-900 mb-2">Customer Information</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
          <div>
            <span className="text-gray-600">Name:</span>
            <span className="ml-2 font-medium">{order.customerName || 'N/A'}</span>
          </div>
          <div>
            <span className="text-gray-600">Email:</span>
            <span className="ml-2 font-medium">{order.customerEmail || 'N/A'}</span>
          </div>
          {order.customerPhone && (
            <div>
              <span className="text-gray-600">Phone:</span>
              <span className="ml-2 font-medium">{order.customerPhone}</span>
            </div>
          )}
        </div>
      </div>

      {/* Line Items */}
      <div className="card">
        <h3 className="font-semibold text-gray-900 mb-3">Items to Fulfill</h3>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                  SKU
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                  Product
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                  Variant
                </th>
                <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase">
                  Quantity
                </th>
                <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase">
                  Price
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                  Sale/Discount
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {order.lineItems && order.lineItems.length > 0 ? (
                order.lineItems.map((item, index) => (
                  <tr key={item.lineItemId || index}>
                    <td className="px-4 py-3 text-sm font-mono text-gray-900">
                      {item.sku || 'N/A'}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-900">
                      {item.title}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {item.variantTitle || '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-right font-medium">
                      {item.quantity}
                    </td>
                    <td className="px-4 py-3 text-sm text-right">
                      ${parseFloat(item.price || 0).toFixed(2)}
                    </td>
                    <td className="px-4 py-3 text-sm">
                      <div className="space-y-1">
                        {/* CRS Sale Badge */}
                        {item.onSale && (
                          <div className="flex items-center gap-1">
                            <span className="inline-block px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-800">
                              🏷️ On Sale in CRS
                            </span>
                            {item.crsSalePrice && (
                              <span className="text-xs text-gray-600">
                                ${parseFloat(item.crsSalePrice).toFixed(2)}
                              </span>
                            )}
                          </div>
                        )}
                        {/* Shopify Discount */}
                        {item.discountAllocations && (
                          <div className="text-xs text-green-700 font-medium">
                            💰 {item.discountAllocations}
                          </div>
                        )}
                        {!item.onSale && !item.discountAllocations && (
                          <span className="text-xs text-gray-400">-</span>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="6" className="px-4 py-3 text-sm text-gray-500 text-center">
                    No items found
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="mt-3 pt-3 border-t border-gray-200 flex justify-between items-center">
          <span className="text-sm text-gray-600">Total Items:</span>
          <span className="text-lg font-bold text-gray-900">{order.itemCount || 0}</span>
        </div>

        {/* Order-level discount summary */}
        {order.totalDiscounts && parseFloat(order.totalDiscounts) > 0 && (
          <div className="mt-3 pt-3 border-t border-gray-200">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-sm font-medium text-gray-700">Order Discounts:</span>
                {order.discountCodes && (
                  <span className="ml-2 text-xs text-gray-500">({order.discountCodes})</span>
                )}
              </div>
              <span className="text-lg font-bold text-green-600">
                -${parseFloat(order.totalDiscounts).toFixed(2)}
              </span>
            </div>
            {order.subtotalPrice && (
              <div className="text-xs text-gray-500 mt-1">
                Subtotal: ${parseFloat(order.subtotalPrice).toFixed(2)} →
                Final: ${parseFloat(order.totalPrice).toFixed(2)}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Shipping Address */}
      {order.shippingAddress && (
        <div className="card bg-green-50 border-green-200">
          <h3 className="font-semibold text-gray-900 mb-2">Shipping Address</h3>
          <div className="text-sm text-gray-700 whitespace-pre-line">
            {order.shippingAddress}
          </div>
        </div>
      )}

      {/* Order Notes */}
      {order.note && (
        <div className="card bg-yellow-50 border-yellow-200">
          <h3 className="font-semibold text-gray-900 mb-2">Order Notes</h3>
          <div className="text-sm text-gray-700">
            {order.note}
          </div>
        </div>
      )}
    </div>
  )
}

OrderDetailCard.propTypes = {
  order: PropTypes.shape({
    customerName: PropTypes.string,
    customerEmail: PropTypes.string,
    customerPhone: PropTypes.string,
    lineItems: PropTypes.arrayOf(
      PropTypes.shape({
        lineItemId: PropTypes.string,
        sku: PropTypes.string,
        title: PropTypes.string,
        variantTitle: PropTypes.string,
        quantity: PropTypes.number,
        price: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      })
    ),
    itemCount: PropTypes.number,
    shippingAddress: PropTypes.string,
    note: PropTypes.string,
  }),
}

export default OrderDetailCard
