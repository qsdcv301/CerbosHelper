package io.cerboshelper.mybatis.model;

public abstract class CerbosCommonDto {
    private String ownerBy;
    private Long ownerGroupBy;

    protected CerbosCommonDto() {
    }

    public String getOwnerBy() {
        return ownerBy;
    }

    public void setOwnerBy(String ownerBy) {
        this.ownerBy = ownerBy;
    }

    public Long getOwnerGroupBy() {
        return ownerGroupBy;
    }

    public void setOwnerGroupBy(Long ownerGroupBy) {
        this.ownerGroupBy = ownerGroupBy;
    }
}
