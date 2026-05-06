package com.order.controller;

import com.order.constants.AppConstants;
import com.order.dto.request.PlaceOrderRequest;
import com.order.dto.response.ApiResponse;
import com.order.dto.response.OrderResponse;
import com.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request) {
        log.info("POST /api/v1/orders - customer: {}", request.getCustomerEmail());
        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(AppConstants.ORDER_CREATED, response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders() {
        log.info("GET /api/v1/orders");
        return ResponseEntity.ok(
                ApiResponse.success("Orders fetched successfully",
                        orderService.getAllOrders())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable Long id) {
        log.info("GET /api/v1/orders/{}", id);
        return ResponseEntity.ok(
                ApiResponse.success("Order fetched successfully",
                        orderService.getOrderById(id))
        );
    }

    @GetMapping("/customer/{email}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getByCustomer(
            @PathVariable String email) {
        log.info("GET /api/v1/orders/customer/{}", email);
        return ResponseEntity.ok(
                ApiResponse.success("Orders fetched successfully",
                        orderService.getOrdersByCustomerEmail(email))
        );
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getByStatus(
            @PathVariable String status) {
        log.info("GET /api/v1/orders/status/{}", status);
        return ResponseEntity.ok(
                ApiResponse.success("Orders fetched successfully",
                        orderService.getOrdersByStatus(status))
        );
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable Long id) {
        log.info("PATCH /api/v1/orders/{}/cancel", id);
        return ResponseEntity.ok(
                ApiResponse.success(AppConstants.ORDER_CANCELLED,
                        orderService.cancelOrder(id))
        );
    }
}