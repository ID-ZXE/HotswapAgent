package org.hotswap.agent.dto;

public class ReloadResultDTO {

    private Long compileCostTime;

    private Long reloadCostTime;

    private Long totalCostTime;

    private Boolean isSuccess;

    public Long getCompileCostTime() {
        return compileCostTime;
    }

    public void setCompileCostTime(Long compileCostTime) {
        this.compileCostTime = compileCostTime;
    }

    public Long getReloadCostTime() {
        return reloadCostTime;
    }

    public void setReloadCostTime(Long reloadCostTime) {
        this.reloadCostTime = reloadCostTime;
    }

    public Long getTotalCostTime() {
        return totalCostTime;
    }

    public void setTotalCostTime(Long totalCostTime) {
        this.totalCostTime = totalCostTime;
    }

    public Boolean getSuccess() {
        return isSuccess;
    }

    public void setSuccess(Boolean success) {
        isSuccess = success;
    }
}
