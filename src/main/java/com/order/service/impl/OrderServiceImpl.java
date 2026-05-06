package com.order.service.impl;

import com.order.constants.AppConstants;
import com.order.dto.external.ProductResponse;
import com.order.dto.request.PlaceOrderRequest;
import com.order.dto.response.OrderResponse;
import com.order.entity.Order;
import com.order.exception.InsufficientStockException;
import com.order.exception.ResourceNotFoundException;
import com.order.exception.ServiceCommunicationException;
import com.order.repository.OrderRepository;
import com.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;

    // Injected from application.properties
    @Value("${inventory.service.url}")
    private String inventoryServiceUrl;

    @Value("${notification.service.url}")
    private String notificationServiceUrl;

    @Override
    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        log.info("Placing order for productId: {}, quantity: {}",
                request.getProductId(), request.getQuantity());

        // ── Step 1: Fetch product from Inventory Service ──
        ProductResponse product = fetchProduct(request.getProductId());

        // ── Step 2: Check stock availability ──────────────
        if (product.getQuantityInStock() < request.getQuantity()) {
            throw new InsufficientStockException(AppConstants.INSUFFICIENT_STOCK);
        }

        // ── Step 3: Deduct stock from Inventory Service ───
        deductStock(request.getProductId(), request.getQuantity());

        // ── Step 4: Calculate total and save order ────────
        BigDecimal totalPrice = product.getPrice()
                .multiply(BigDecimal.valueOf(request.getQuantity()));

        Order order = Order.builder()
                .productId(product.getId())
                .productSku(product.getSku())
                .productName(product.getName())
                .quantity(request.getQuantity())
                .unitPrice(product.getPrice())
                .totalPrice(totalPrice)
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .status(AppConstants.STATUS_CONFIRMED)
                .build();

        Order savedOrder = orderRepository.save(order);
        log.info("Order saved with id: {}", savedOrder.getId());

        // ── Step 5: Send notification ─────────────────────
        sendNotification(
                AppConstants.NOTIF_ORDER_PLACED,
                String.format("Order #%d placed by %s for %s x%d",
                        savedOrder.getId(),
                        request.getCustomerName(),
                        product.getName(),
                        request.getQuantity()),
                String.valueOf(savedOrder.getId())
        );

        return mapToResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        return mapToResponse(findOrderById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomerEmail(String email) {
        return orderRepository.findByCustomerEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByStatus(String status) {
        return orderRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id) {
        log.info("Cancelling order with id: {}", id);
        Order order = findOrderById(id);

        if (order.getStatus().equals(AppConstants.STATUS_CANCELLED)) {
            throw new IllegalStateException("Order is already cancelled");
        }

        order.setStatus(AppConstants.STATUS_CANCELLED);
        Order saved = orderRepository.save(order);

        sendNotification(
                AppConstants.NOTIF_ORDER_CANCELLED,
                String.format("Order #%d has been cancelled", id),
                String.valueOf(id)
        );

        return mapToResponse(saved);
    }

    // ─── Private: Inter-Service Calls ───────────────────

    /**
     * Calls GET /api/v1/products/{id} on Inventory Service.
     * Returns the product details including price and stock.
     */
    private ProductResponse fetchProduct(Long productId) {
        String url = inventoryServiceUrl + "/api/v1/products/" + productId;
        log.info("Calling Inventory Service: GET {}", url);

        try {
            // RestTemplate.getForEntity sends HTTP GET and
            // deserializes the response body into ApiResponse
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getBody() == null || !Boolean.TRUE.equals(
                    ((Map<?, ?>) response.getBody()).get("success"))) {
                throw new ResourceNotFoundException(AppConstants.PRODUCT_NOT_FOUND);
            }

            // Extract the 'data' map and convert to ProductResponse
            Map<?, ?> data = (Map<?, ?>) ((Map<?, ?>) response.getBody()).get("data");
            return mapToProductResponse(data);

        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException(AppConstants.PRODUCT_NOT_FOUND);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling Inventory Service: {}", e.getMessage());
            throw new ServiceCommunicationException(AppConstants.INVENTORY_ERROR);
        }
    }

    /**
     * Calls PATCH /api/v1/products/{id}/deduct-stock on Inventory Service.
     * Atomically deducts the requested quantity from stock.
     */
    private void deductStock(Long productId, Integer quantity) {
        String url = inventoryServiceUrl + "/api/v1/products/" + productId + "/deduct-stock";
        log.info("Calling Inventory Service: PATCH {}", url);

        try {
            Map<String, Integer> body = new HashMap<>();
            body.put("quantity", quantity);
            restTemplate.exchange(url, org.springframework.http.HttpMethod.PATCH, new org.springframework.http.HttpEntity<>(body), Map.class);
        } catch (HttpClientErrorException e) {
            log.error("Stock deduction failed: {}", e.getMessage());
            throw new InsufficientStockException(AppConstants.INSUFFICIENT_STOCK);
        } catch (Exception e) {
            log.error("Error calling Inventory Service: {}", e.getMessage());
            throw new ServiceCommunicationException(AppConstants.INVENTORY_ERROR);
        }
    }

    /**
     * Calls POST /api/v1/notifications on Notification Service.
     * Fire-and-forget — we log errors but don't fail the order
     * if notification fails. Order is already saved at this point.
     */
    private void sendNotification(String type, String message, String referenceId) {
        String url = notificationServiceUrl + "/api/v1/notifications";
        log.info("Calling Notification Service: POST {}", url);

        try {
            Map<String, String> body = new HashMap<>();
            body.put("type", type);
            body.put("message", message);
            body.put("referenceId", referenceId);
            restTemplate.postForObject(url, body, Map.class);
            log.info("Notification sent successfully");
        } catch (Exception e) {
            // Non-critical — log but don't throw
            // Order is saved, notification failure shouldn't
            // roll back the entire transaction
            log.error("Failed to send notification: {}", e.getMessage());
        }
    }

    // ─── Private: Helpers ───────────────────────────────

    private Order findOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        AppConstants.ORDER_NOT_FOUND + id
                ));
    }

    private ProductResponse mapToProductResponse(Map<?, ?> data) {
        ProductResponse p = new ProductResponse();
        p.setId(((Number) data.get("id")).longValue());
        p.setName((String) data.get("name"));
        p.setSku((String) data.get("sku"));
        p.setPrice(new BigDecimal(data.get("price").toString()));
        p.setQuantityInStock(((Number) data.get("quantityInStock")).intValue());
        p.setActive((Boolean) data.get("active"));
        return p;
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .productId(order.getProductId())
                .productSku(order.getProductSku())
                .productName(order.getProductName())
                .quantity(order.getQuantity())
                .unitPrice(order.getUnitPrice())
                .totalPrice(order.getTotalPrice())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}