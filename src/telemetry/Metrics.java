package telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;

public class Metrics {

    private static final Meter meter =
            GlobalOpenTelemetry.get().meterBuilder("file-transfer").build();

    public static final LongCounter filesSent =
            meter.counterBuilder("files_sent_total")
                    .setDescription("Number of files successfully sent by the client")
                    .setUnit("1")
                    .build();

    public static final LongCounter filesReceived =
            meter.counterBuilder("files_received_total")
                    .setDescription("Number of files successfully received by the server")
                    .setUnit("1")
                    .build();

    public static final DoubleHistogram transferLatency =
            meter.histogramBuilder("file_transfer_latency_ms")
                    .setDescription("End-to-end file transfer latency")
                    .setUnit("ms")
                    .build();

    private Metrics() {}
}
