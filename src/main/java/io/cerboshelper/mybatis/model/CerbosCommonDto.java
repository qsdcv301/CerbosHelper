package io.cerboshelper.mybatis.model;

public abstract class CerbosCommonDto {
    private String ownerBy;
    private Long ownerOrgBy;

    protected CerbosCommonDto() {
    }

    public String getOwnerBy() {
        return ownerBy;
    }

    public void setOwnerBy(String ownerBy) {
        this.ownerBy = ownerBy;
    }

    public Long getOwnerOrgBy() {
        return ownerOrgBy;
    }

    public void setOwnerOrgBy(Long ownerOrgBy) {
        this.ownerOrgBy = ownerOrgBy;
    }
}
