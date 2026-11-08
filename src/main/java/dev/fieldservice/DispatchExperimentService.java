package dev.fieldservice;

import java.util.List;

public final class DispatchExperimentService {
    private DispatchExperimentService() {}

    public static void main(String[] args) {
        String experimentKey = System.getenv().getOrDefault("EXPERIMENT_KEY", "photo-follow-up-layout");
        InfraiClient infrai = new InfraiClient(InfraiConfig.fromEnvironment());
        WorkOrderAssignmentService service = new WorkOrderAssignmentService(infrai, experimentKey);

        WorkOrderAssignmentService.WorkOrder order = new WorkOrderAssignmentService.WorkOrder(
                "WO-1842", "tech-27", List.of("photo-front", "photo-meter"), "DISPATCHED", true);
        WorkOrderAssignmentService.Assignment result = service.assign(order);

        System.out.printf("workOrder=%s technician=%s bucket=%s experience=%s photos=%d status=%s followUp=%s assignmentId=%s%n",
                result.workOrderId(), result.technicianId(), result.bucket(), result.experience(),
                result.photoCount(), result.dispatchStatus(), result.followUpRequired(),
                WorkOrderAssignmentService.assignmentId(experimentKey, result.technicianId()));
    }
}
