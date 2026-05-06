package com.order.constants;

public final class AppConstants {

    private AppConstants() {}

    // ─── Success Messages ───────────────────────────
    public static final String ORDER_CREATED        = "Order placed successfully";
    public static final String ORDER_CANCELLED      = "Order cancelled successfully";

    // ─── Error Messages ─────────────────────────────
    public static final String ORDER_NOT_FOUND      = "Order not found with id: ";
    public static final String INSUFFICIENT_STOCK   = "Insufficient stock for the requested quantity";
    public static final String PRODUCT_NOT_FOUND    = "Product not found in inventory";
    public static final String INVENTORY_ERROR      = "Error communicating with Inventory Service";
    public static final String NOTIFICATION_ERROR   = "Error communicating with Notification Service";

    // ─── Order Status ────────────────────────────────
    public static final String STATUS_PENDING       = "PENDING";
    public static final String STATUS_CONFIRMED     = "CONFIRMED";
    public static final String STATUS_CANCELLED     = "CANCELLED";

    // ─── Notification Types ──────────────────────────
    public static final String NOTIF_ORDER_PLACED    = "ORDER_PLACED";
    public static final String NOTIF_ORDER_CANCELLED = "ORDER_CANCELLED";
}