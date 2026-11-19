package com.dms21.cmdbadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 集群（部署单元）：应用在某套环境的部署单元，对应一个 Eureka 注册名。
 *
 * 层级：应用（t_service_group）→ 集群（本表）→ 实例（运行态，后续落库）
 * 一个应用可以有多个集群（dev1 / dev2 / stg1 / prd），每个集群的注册名全局唯一。
 */
@Entity
@Table(name = "t_service_cluster")
public class ServiceClusterEntity {

    /** 集群状态下拉选项 */
    public static final java.util.List<String> STATUS_OPTIONS = java.util.List.of("在运", "停用");

    /** 部署方式下拉选项 */
    public static final java.util.List<String> DEPLOY_TYPE_OPTIONS = java.util.List.of("容器", "虚拟机", "物理机");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属应用 id（t_service_group 主键） */
    @Column(name = "app_id", nullable = false)
    private Long appId;

    /** Eureka 注册名（spring.application.name），全局唯一 */
    @Column(name = "registry_name", unique = true, nullable = false, length = 128)
    private String registryName;

    /** 集群名，如 dev1 / prd / gray；同应用同环境内唯一 */
    @Column(name = "cluster_name", length = 64)
    private String clusterName;

    /** 环境 id（t_env 主键），空表示未指定 */
    @Column(name = "env_id")
    private Long envId;

    /** 机房，如 SH-IDC-A */
    @Column(name = "idc", length = 64)
    private String idc;

    /** 容器集群，如 k8s-prod-sh-01；虚拟机部署留空 */
    @Column(name = "k8s_cluster", length = 128)
    private String k8sCluster;

    /** 命名空间，如 trade-prod */
    @Column(name = "namespace", length = 128)
    private String namespace;

    /** 部署方式：容器 / 虚拟机 / 物理机 */
    @Column(name = "deploy_type", length = 32)
    private String deployType;

    /** 期望副本数 */
    @Column(name = "replicas")
    private Integer replicas;

    /** 访问入口（域名 / 网关地址），可空 */
    @Column(name = "entry_url", length = 300)
    private String entryUrl;

    /** 集群状态：在运 / 停用 */
    @Column(name = "status", length = 16, columnDefinition = "varchar(16) default '在运'")
    private String status = "在运";

    /** 认领状态：待认领（Eureka 自动发现未绑定应用）/ 已绑定（人工创建或人工绑定） */
    @Column(name = "claim_status", length = 16, columnDefinition = "varchar(16) default '已绑定'")
    private String claimStatus = "已绑定";

    /** 扩展信息（JSON 字符串） */
    @Column(name = "ext", columnDefinition = "varchar(4000)")
    private String ext;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public String getRegistryName() {
        return registryName;
    }

    public void setRegistryName(String registryName) {
        this.registryName = registryName;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public Long getEnvId() {
        return envId;
    }

    public void setEnvId(Long envId) {
        this.envId = envId;
    }

    public String getIdc() {
        return idc;
    }

    public void setIdc(String idc) {
        this.idc = idc;
    }

    public String getK8sCluster() {
        return k8sCluster;
    }

    public void setK8sCluster(String k8sCluster) {
        this.k8sCluster = k8sCluster;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getDeployType() {
        return deployType;
    }

    public void setDeployType(String deployType) {
        this.deployType = deployType;
    }

    public Integer getReplicas() {
        return replicas;
    }

    public void setReplicas(Integer replicas) {
        this.replicas = replicas;
    }

    public String getEntryUrl() {
        return entryUrl;
    }

    public void setEntryUrl(String entryUrl) {
        this.entryUrl = entryUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getClaimStatus() {
        return claimStatus;
    }

    public void setClaimStatus(String claimStatus) {
        this.claimStatus = claimStatus;
    }

    public String getExt() {
        return ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
