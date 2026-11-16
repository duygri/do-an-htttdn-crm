package com.htttdn.crm.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "stocktake_items", uniqueConstraints = @UniqueConstraint(name = "uq_stocktake_item_product", columnNames = {"stocktake_id", "product_id"}))
public class StocktakeItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "stocktake_id", nullable = false)
    private Stocktake stocktake;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "system_quantity", nullable = false)
    private int systemQuantity;

    @Column(name = "counted_quantity")
    private Integer countedQuantity;

    private Integer difference;

    @Column(length = 500)
    private String note;

    public Long getId() { return id; }
    public Stocktake getStocktake() { return stocktake; }
    public void setStocktake(Stocktake stocktake) { this.stocktake = stocktake; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public int getSystemQuantity() { return systemQuantity; }
    public void setSystemQuantity(int systemQuantity) { this.systemQuantity = systemQuantity; }
    public Integer getCountedQuantity() { return countedQuantity; }
    public void setCountedQuantity(Integer countedQuantity) { this.countedQuantity = countedQuantity; }
    public Integer getDifference() { return difference; }
    public void setDifference(Integer difference) { this.difference = difference; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
