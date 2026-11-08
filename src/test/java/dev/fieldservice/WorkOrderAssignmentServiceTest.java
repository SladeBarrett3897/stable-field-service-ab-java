package dev.fieldservice;

import java.util.List;

public final class WorkOrderAssignmentServiceTest {
    public static void main(String[] args) {
        FieldServiceGateway gateway = new FieldServiceGateway() {
            public String experimentValue(String key) { return "compact-photo-review"; }
            public String technicianFollowUpHistory() { return "[{\"status\":\"completed\"}]"; }
        };
        WorkOrderAssignmentService service = new WorkOrderAssignmentService(gateway, "photo-follow-up-layout");
        var first = service.assign(new WorkOrderAssignmentService.WorkOrder(
                "WO-101", "tech-27", List.of("meter", "panel"), "DISPATCHED", true));
        var later = service.assign(new WorkOrderAssignmentService.WorkOrder(
                "WO-209", "tech-27", List.of("arrival"), "ON_SITE", true));

        check(first.bucket().equals(later.bucket()), "the same technician moved buckets");
        check(first.experience().equals("compact-photo-review"), "flag value was not applied");
        check(first.photoCount() == 2, "work-order photos were not modeled");
        check(first.followUpHistory().contains("completed"), "follow-up history was not attached");
        System.out.println("PASS: tech-27 keeps bucket " + first.bucket() + " across work orders");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
