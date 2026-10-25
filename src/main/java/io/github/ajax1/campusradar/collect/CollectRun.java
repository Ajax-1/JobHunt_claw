package io.github.ajax1.campusradar.collect;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.Instant;

/**
 * 每个数据源每次采集一条记录，用于健康检查和排查问题
 */
@Entity
@Table(name = "collect_run", indexes = @Index(name = "idx_run_source_started", columnList = "source,startedAt"))
public class CollectRun {

    public enum Status {
        RUNNING,
        /**
         * 正常完成
         */
        SUCCESS,
        /**
         * 完成了，但数据量异常（比如比上次少一半以上），需要人看一眼
         */
        WARN,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    private int fetched;
    private int created;
    private int updated;
    private int closed;
    private int detailFailed;
    private int requests;

    @Column(length = 1000)
    private String message;

    @Transient
    private String firstDetailError;

    protected CollectRun() {
    }

    public CollectRun(String source, Instant startedAt) {
        this.source = source;
        this.startedAt = startedAt;
        this.status = Status.RUNNING;
    }

    public void finish(Status status, String message) {
        this.status = status;
        this.finishedAt = Instant.now();
        this.message = message == null || message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getFetched() {
        return fetched;
    }

    public void setFetched(int fetched) {
        this.fetched = fetched;
    }

    public int getCreated() {
        return created;
    }

    public int getUpdated() {
        return updated;
    }

    public int getClosed() {
        return closed;
    }

    public void setClosed(int closed) {
        this.closed = closed;
    }

    public int getDetailFailed() {
        return detailFailed;
    }

    public int getRequests() {
        return requests;
    }

    public void setRequests(int requests) {
        this.requests = requests;
    }

    public String getMessage() {
        return message;
    }

    void count(JobUpsertService.Outcome outcome) {
        switch (outcome) {
            case CREATED -> created++;
            case UPDATED -> updated++;
            case UNCHANGED -> {
            }
        }
    }

    void detailFailed(String error) {
        if (detailFailed++ == 0) {
            firstDetailError = error;
        }
    }

    /**
     * 详情拉取失败时附在运行结果里的说明，没有失败返回 null
     */
    String detailNote() {
        return detailFailed == 0 ? null : "详情失败 " + detailFailed + " 条，首个错误：" + firstDetailError;
    }
}
