package com.order.dto.external;

import lombok.Data;

import java.math.BigDecimal;


@Data
public class ProductResponse {

    private Long id;
    private String name;
    private String sku;
    private String description;
    private BigDecimal price;
    private Integer quantityInStock;
    private String category;
    private Boolean active;
}