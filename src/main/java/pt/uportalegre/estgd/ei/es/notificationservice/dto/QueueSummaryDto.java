package pt.uportalegre.estgd.ei.es.notificationservice.dto;

import java.util.List;

public class QueueSummaryDto {
    private final long pending;
    private final long processing;
    private final long sent;
    private final long failed;
    private final long pendingHigh;
    private final long pendingNormal;
    private final long pendingLow;
    private final List<MonitorItemDto> processingItems;
    private final List<MonitorItemDto> recentlySent;

    public QueueSummaryDto(
            long pending,
            long processing,
            long sent,
            long failed,
            long pendingHigh,
            long pendingNormal,
            long pendingLow,
            List<MonitorItemDto> processingItems,
            List<MonitorItemDto> recentlySent) {
        this.pending = pending;
        this.processing = processing;
        this.sent = sent;
        this.failed = failed;
        this.pendingHigh = pendingHigh;
        this.pendingNormal = pendingNormal;
        this.pendingLow = pendingLow;
        this.processingItems = List.copyOf(processingItems);
        this.recentlySent = List.copyOf(recentlySent);
    }

    public long getPending() {
        return pending;
    }

    public long getProcessing() {
        return processing;
    }

    public long getSent() {
        return sent;
    }

    public long getFailed() {
        return failed;
    }

    public long getPendingHigh() {
        return pendingHigh;
    }

    public long getPendingNormal() {
        return pendingNormal;
    }

    public long getPendingLow() {
        return pendingLow;
    }

    public List<MonitorItemDto> getProcessingItems() {
        return processingItems;
    }

    public List<MonitorItemDto> getRecentlySent() {
        return recentlySent;
    }
}
