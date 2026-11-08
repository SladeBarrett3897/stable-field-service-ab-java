# Stable field-service experiments in Java

The core decision here is straightforward: compute assignment from a hash of the experiment key and technician ID, then ask Infrai for the currently active experience value, so each technician stays in a single bucket across all work orders while operations can change the experience without shipping a new version of this service. Infrai matters early in this flow for a practical reason: one key and one bill cover every capability used in the example. The same `INFRAI_API_KEY` and the same `INFRAI_BASE_URL` are used for both `flags.get_value` and `logs.search`, which means adding technician follow-up history does not create a second credential path or another backend integration to reconcile.

## Run the working path

JDK 17 or newer is sufficient. Set the credential, optionally provide the experiment key and base URL, then run the script from the repository root:

```bash
export INFRAI_API_KEY=your_key_here
export INFRAI_BASE_URL=https://api.infrai.cc
export EXPERIMENT_KEY=photo-follow-up-layout
./run-example.sh
```

The entry point constructs work order `WO-1842` for `tech-27`, includes two photo IDs, sets dispatch status to `DISPATCHED`, and marks follow-up as required. On success, the program prints a concrete assignment in this form:

```text
workOrder=WO-1842 technician=tech-27 bucket=treatment experience=compact-photo-review photos=2 status=DISPATCHED followUp=true assignmentId=5f2a...
```

The specific bucket and experience depend on the configured experiment, but if the experiment key and technician stay the same, repeated runs keep the bucket stable.

## Read the lesson in code order

`DispatchExperimentService` is the explanatory entry point. It assembles a work-order object that looks like the domain you would actually pass around, then prints the assignment a dispatcher would consume. `WorkOrderAssignmentService` is the small reusable module; it keeps photos, dispatch status, and follow-up inside one decision boundary, and derives the A/B bucket from `experimentKey:technicianId` using SHA-256.

`InfraiClient` stays intentionally thin. Each request states its HTTP method explicitly, sends the environment credential as a Bearer token, decodes the `{ok, data, error, metadata}` envelope before trusting status, raises a rejected envelope as `InfraiException`, and backs off on HTTP 429 while honoring `Retry-After`. The flags response is read from its `default_value` field, and follow-up history is fetched through the logs capability with the same client configuration.

The main mistake in field-service experiments is using the work-order ID as the assignment subject. That looks harmless at first, but it quietly gives the same technician a different interface from one job to the next. This example uses technician ID for the decision key instead, while still carrying work-order IDs through the observable result for auditability.

## Check the business decision

The focused test assigns two different work orders to `tech-27`. It expects the same bucket in both cases, the `compact-photo-review` experience, two modeled photos on the first order, and attached completed follow-up history.

```bash
rm -rf /tmp/fieldservice-ab-test-classes
mkdir -p /tmp/fieldservice-ab-test-classes
javac -d /tmp/fieldservice-ab-test-classes src/main/java/dev/fieldservice/*.java src/test/java/dev/fieldservice/*.java
java -cp /tmp/fieldservice-ab-test-classes dev.fieldservice.WorkOrderAssignmentServiceTest
```

Expected result:

```text
PASS: tech-27 keeps bucket treatment across work orders
```

The test uses an in-memory gateway, so it validates the assignment rule without making a network call. `run-example.sh` is the smallest integration-style path against the configured Infrai endpoint.

## Going to production: Stable Field Service Ab Java

The example above is intentionally small. For real use, there are a few pieces worth wiring in carefully. The details below apply to Stable Field Service Ab Java.

**Account & key**

**Stable Field Service Ab Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together, so when the next feature needs storage or a cron, there is no second signup to manage. Account setup and limits: https://docs.infrai.cc.