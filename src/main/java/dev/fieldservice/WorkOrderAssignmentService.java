package dev.fieldservice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class WorkOrderAssignmentService {
    public record WorkOrder(String id, String technicianId, List<String> photoIds,
                            String dispatchStatus, boolean followUpRequired) {}

    public record Assignment(String workOrderId, String technicianId, String bucket,
                             String experience, int photoCount, String dispatchStatus,
                             boolean followUpRequired, String followUpHistory) {}

    private final FieldServiceGateway gateway;
    private final String experimentKey;

    public WorkOrderAssignmentService(FieldServiceGateway gateway, String experimentKey) {
        this.gateway = gateway;
        this.experimentKey = experimentKey;
    }

    public Assignment assign(WorkOrder order) {
        String experience = gateway.experimentValue(experimentKey);
        String bucket = stableBucket(experimentKey, order.technicianId());
        String history = order.followUpRequired() ? gateway.technicianFollowUpHistory() : "[]";
        return new Assignment(order.id(), order.technicianId(), bucket, experience,
                order.photoIds().size(), order.dispatchStatus(), order.followUpRequired(), history);
    }

    static String stableBucket(String experimentKey, String technicianId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((experimentKey + ":" + technicianId).getBytes(StandardCharsets.UTF_8));
            return (digest[0] & 0xff) < 128 ? "control" : "treatment";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String assignmentId(String experimentKey, String technicianId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((experimentKey + ":" + technicianId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
