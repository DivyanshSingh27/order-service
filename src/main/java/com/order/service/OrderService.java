package com.order.service;

import com.order.dto.request.PlaceOrderRequest;
import com.order.dto.response.OrderResponse;

import java.util.List;

public interface OrderService {

    OrderResponse placeOrder(PlaceOrderRequest request);

    OrderResponse getOrderById(Long id);

    List<OrderResponse> getAllOrders();

    List<OrderResponse> getOrdersByCustomerEmail(String email);

    List<OrderResponse> getOrdersByStatus(String status);

    OrderResponse cancelOrder(Long id);
}