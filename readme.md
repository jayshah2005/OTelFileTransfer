# OTelFileTransfer: Distributed File Transfer with OpenTelemetry

A Java-based client-server file transfer system instrumented with OpenTelemetry for distributed tracing, performance monitoring, and observability. This project demonstrates end-to-end observability in a distributed system using OpenTelemetry, Jaeger, Prometheus, and Grafana.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Setup and Installation](#setup-and-installation)
- [Usage](#usage)
- [OpenTelemetry Instrumentation](#opentelemetry-instrumentation)
- [Advanced Features](#advanced-features)
- [Sampling Strategies](#sampling-strategies)
- [Visualization and Monitoring](#visualization-and-monitoring)
- [Configuration](#configuration)
- [Performance Analysis](#performance-analysis)
- [Troubleshooting](#troubleshooting)

## Overview

This project implements a distributed file transfer system where:
- **Client**: Reads files from a local directory, compresses them, calculates checksums, and sends them to the server in chunks
- **Server**: Receives files, verifies data integrity, decompresses, and saves them to an output directory
- **Observability**: Full instrumentation with OpenTelemetry for tracing, metrics, and distributed system analysis

The system supports concurrent file transfers, implements multiple advanced features (compression, checksums, chunking), and provides comprehensive observability through OpenTelemetry instrumentation.

## Features

### Core Functionality
- ✅ **Client-Server Architecture**: TCP-based file transfer system
- ✅ **Concurrent Processing**: Multithreaded server supporting multiple simultaneous client connections
- ✅ **File Management**: Automatic file discovery and batch processing

### Advanced Features Implemented
- ✅ **Compression**: GZIP compression/decompression to optimize transfer time
- ✅ **Data Integrity**: SHA-256 checksums to verify transferred data integrity
- ✅ **Chunking & Streaming**: Large files transferred in 8KB chunks for efficient memory usage

### Observability Features
- ✅ **Distributed Tracing**: End-to-end trace collection with custom spans
- ✅ **Custom Metrics**: Counters and histograms for performance monitoring
- ✅ **Span Events**: Detailed event logging at key execution points
- ✅ **Sampling Strategies**: Support for AlwaysOn and Probability sampling
- ✅ **Automatic Instrumentation**: Java agent for automatic network/HTTP instrumentation

## Architecture

```
┌─────────────┐         ┌──────────────┐         ┌─────────────────┐
│   Client    │────────▶│    Server    │         │  OpenTelemetry  │
│             │  TCP    │  (Threaded)  │         │    Collector    │
│ - Compress  │         │ - Decompress │         │                 │
│ - Checksum  │         │ - Verify     │         │                 │
│ - Chunk     │         │ - Save       │         │                 │
└─────────────┘         └──────────────┘         └────────┬────────┘
                                                           │
                                    ┌──────────────────────┼──────────────────────┐
                                    │                      │                      │
                           ┌────────▼────────┐   ┌────────▼────────┐   ┌────────▼────────┐
                           │     Jaeger      │   │   Prometheus    │   │    Grafana      │
                           │  (Tracing UI)   │   │   (Metrics DB)  │   │  (Dashboards)   │
                           └─────────────────┘   └─────────────────┘   └─────────────────┘
```

### Component Details

**Client (`Client.java`, `ClientRunner.java`)**
- Discovers files in input directory
- Compresses files using GZIP
- Calculates SHA-256 checksums
- Sends files in chunks via TCP socket
- Creates multiple threads for concurrent transfers

**Server (`FileServer.java`, `ServerThread.java`)**
- Listens on configurable port (default: 5050)
- Accepts multiple concurrent connections
- Verifies checksums before saving
- Decompresses received files
- Writes to output directory

**Telemetry (`TelemetryConfig.java`, `Metrics.java`)**
- Configures OpenTelemetry SDK
- Implements custom samplers (AlwaysOn, Probability)
- Defines custom metrics (counters, histograms)
- Manages trace export to OTLP collector

## Prerequisites

- **Java**: JDK 11 or higher
- **Docker & Docker Compose**: For running observability stack
- **Maven or Gradle**: For dependency management (or manual JAR management)
- **OpenTelemetry Java Agent**: Included in `src/otel/opentelemetry-javaagent.jar`

## Project Structure

```
COSC3P95Assignment2Q1/
├── src/
│   ├── client/
│   │   ├── Client.java              # Main client logic with tracing
│   │   └── ClientRunner.java         # Client entry point and file discovery
│   ├── server/
│   │   ├── FileServer.java           # Server main class
│   │   └── ServerThread.java         # Thread handler for each client connection
│   ├── telemetry/
│   │   ├── TelemetryConfig.java      # OpenTelemetry SDK configuration
│   │   └── Metrics.java              # Custom metrics definitions
│   ├── Configs/
│   │   └── Config.java               # Global configuration (port, host)
│   ├── UtilityFunctions/
│   │   └── FileGenerator.java        # Utility to generate test files
│   └── otel/
│       └── opentelemetry-javaagent.jar  # OpenTelemetry Java agent
├── docker/
│   ├── docker-compose.yml            # Docker services (Jaeger, Collector, Prometheus, Grafana)
│   ├── otel-config/
│   │   └── otel-collector.yaml       # OpenTelemetry Collector configuration
│   └── prometheus/
│       └── prometheus.yml             # Prometheus scrape configuration
├── files2transfer/                   # Input directory for client files
├── server-out/                        # Output directory for received files
└── README.md                          # This file
```

## Setup and Installation

### 1. Start Docker Services

First, start the observability stack:

```bash
cd docker
docker compose up -d
```

This will start:
- **Jaeger UI**: http://localhost:16686
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (username: `admin`, password: `admin`)
- **OTLP Collector**: http://localhost:4317

### 2. Generate Test Files (Optional)

If you need to generate test files:

```bash
cd src
javac UtilityFunctions/FileGenerator.java
java UtilityFunctions.FileGenerator
```

This creates 20 files of varying sizes (5 KB to 100 MB) in the `files2transfer/` directory.

### 3. Compile the Project

Compile all Java source files:

```bash
cd src
javac -cp "lib/*:otel/*" client/*.java server/*.java telemetry/*.java Configs/*.java UtilityFunctions/*.java
```

## Usage

### Running the Server

Start the server with OpenTelemetry instrumentation:

```bash
cd src
java -javaagent:./otel/opentelemetry-javaagent.jar \
     -Dotel.service.name=file-server \
     -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -Dotel.exporter.otlp.protocol=grpc \
     -Dotel.metrics.exporter=otlp \
     -Dotel.traces.sampler=traceidratio \
     -Dotel.traces.sampler.arg=0.2 \
     -cp ".:lib/*:otel/*" \
     server.FileServer [output-directory]
```

**Parameters:**
- `-javaagent`: Enables automatic OpenTelemetry instrumentation
- `-Dotel.service.name`: Service name for traces
- `-Dotel.exporter.otlp.endpoint`: OTLP collector endpoint
- `-Dotel.traces.sampler.arg`: Sampling ratio (0.2 = 20% probability sampling)

**For AlwaysOn sampling**, change the sampler argument:
```bash
-Dotel.traces.sampler=always_on
```

### Running the Client

Start the client to transfer files:

```bash
cd src
java -javaagent:./otel/opentelemetry-javaagent.jar \
     -Dotel.service.name=file-client \
     -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -Dotel.exporter.otlp.protocol=grpc \
     -Dotel.metrics.exporter=otlp \
     -Dotel.traces.sampler=traceidratio \
     -Dotel.traces.sampler.arg=0.2 \
     -cp ".:lib/*:otel/*" \
     client.ClientRunner [input-directory]
```

**Default directories:**
- Input directory: `files2transfer/` (if not specified)
- Output directory: `server-out/` (if not specified)

### Example Workflow

1. **Terminal 1**: Start Docker services
   ```bash
   cd docker && docker compose up -d
   ```

2. **Terminal 2**: Start the server
   ```bash
   # Use AlwaysOn sampling
   java -javaagent:./src/otel/opentelemetry-javaagent.jar \
        -Dotel.service.name=file-server \
        -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
        -Dotel.exporter.otlp.protocol=grpc \
        -Dotel.metrics.exporter=otlp \
        -Dotel.traces.sampler=always_on \
        -cp ".:src/lib/*:src/otel/*" \
        server.FileServer
   ```

3. **Terminal 3**: Run the client
   ```bash
   java -javaagent:./src/otel/opentelemetry-javaagent.jar \
        -Dotel.service.name=file-client \
        -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
        -Dotel.exporter.otlp.protocol=grpc \
        -Dotel.metrics.exporter=otlp \
        -Dotel.traces.sampler=always_on \
        -cp ".:src/lib/*:src/otel/*" \
        client.ClientRunner
   ```

## OpenTelemetry Instrumentation

### Manual Instrumentation

The project includes extensive manual instrumentation with custom spans:

#### Client-Side Spans
- **`ClientRunner`**: Root span for entire client execution
- **`Connect to Server`**: Connection establishment with network attributes
- **`sendSingleFile`**: File transfer operation with file metadata
- **`compressFile (GZIP)`**: Compression operation with compression ratio tracking
- **`calculateChecksum (SHA-256)`**: Checksum calculation span
- **`Transfer Data Chunks`**: Chunked data transfer with chunk tracking

#### Server-Side Spans
- **`ServerThread`**: Root span for server thread execution
- **`receiveAndSaveFile`**: File reception and processing
- **`readCompressedBytes`**: Reading compressed data from network
- **`verifyChecksum`**: Checksum verification with expected/actual values
- **`decompress`**: Decompression operation
- **`writeToDisk`**: File write operation

### Custom Attributes

Spans include meaningful attributes:
- `file.name`: Original filename
- `file.original.size`: Original file size in bytes
- `file.compressed.size`: Compressed file size in bytes
- `file.decompressed.size`: Decompressed file size
- `net.peer.name`: Server hostname
- `net.peer.port`: Server port
- `peer.address`: Full peer address
- `data.size`: Data size for checksum calculation
- `expected` / `actual`: Checksum values for verification

### Span Events

Key execution points are logged as span events:
- `send_file_started` / `send_file_completed`
- `compression_started` / `compression_completed`
- `checksum_started` / `checksum_completed`
- `transfer_started` / `transfer_completed`
- `termination_signal_received`
- `client_disconnected_eof`

### Custom Metrics

Two custom metrics are defined in `Metrics.java`:

1. **`files_sent_total`** (Counter)
   - Description: Number of files successfully sent by the client
   - Unit: 1 (count)
   - Incremented after each successful file transfer

2. **`files_received_total`** (Counter)
   - Description: Number of files successfully received by the server
   - Unit: 1 (count)
   - Incremented after each successful file save

3. **`file_transfer_latency_ms`** (Histogram)
   - Description: End-to-end file transfer latency
   - Unit: milliseconds
   - Records latency for each file transfer operation

### Automatic Instrumentation

The OpenTelemetry Java agent automatically instruments:
- Network operations (Socket I/O)
- HTTP operations (if used)
- JVM metrics
- System metrics

## Advanced Features

### 1. Compression (GZIP)

**Implementation**: Files are compressed using Java's `GZIPOutputStream` before transmission.

**Benefits**:
- Reduces network bandwidth usage
- Faster transfer times for compressible files
- Compression ratio tracked in spans

**Code Location**: `Client.java::compressFile()`, `ServerThread.java::decompress()`

### 2. Data Integrity (SHA-256 Checksums)

**Implementation**: SHA-256 checksums are calculated on compressed data and verified on the server.

**Process**:
1. Client calculates checksum after compression
2. Checksum sent as metadata before file data
3. Server calculates checksum on received data
4. Comparison performed before saving file

**Code Location**: `Client.java::calculateChecksumTraced()`, `ServerThread.java::verifyChecksum()`

### 3. Chunking & Streaming

**Implementation**: Large files are transferred in 8KB chunks to optimize memory usage.

**Benefits**:
- Efficient memory management
- Better handling of large files (up to 100 MB)
- Progress tracking capability

**Code Location**: `Client.java::sendSingleFile()` (chunked write loop)

## Sampling Strategies

The project supports two sampling strategies:

### AlwaysOn Sampling

**Configuration**:
```bash
-Dotel.traces.sampler=always_on
```

**Characteristics**:
- 100% of traces are collected
- Maximum observability
- Higher overhead and storage requirements
- Best for debugging and development

### Probability Sampling (TraceIdRatioBased)

**Configuration**:
```bash
-Dotel.traces.sampler=traceidratio
-Dotel.traces.sampler.arg=0.2  # 20% sampling rate
```

**Characteristics**:
- Configurable sampling rate (0.0 to 1.0)
- Reduces overhead and storage
- Statistical representation of system behavior
- Recommended for production (10-40% range)

**Comparison**:
- **AlwaysOn**: Full visibility, higher cost
- **Probability (20%)**: 80% reduction in trace volume, maintains statistical accuracy

## Visualization and Monitoring

### Jaeger (Tracing)

**Access**: http://localhost:16686

**Features**:
- View distributed traces
- Analyze span relationships
- Filter by service, operation, tags
- Timeline visualization
- Trace comparison

**Usage**:
1. Open Jaeger UI
2. Select service: `file-client` or `file-server`
3. Click "Find Traces"
4. Explore trace details and spans

### Prometheus (Metrics)

**Access**: http://localhost:9090

**Features**:
- Query custom metrics
- Time-series data storage
- PromQL query language

**Example Queries**:
```promql
# Total files sent
rate(files_sent_total[5m])

# Average transfer latency
histogram_quantile(0.95, file_transfer_latency_ms_bucket)

# Files received per second
rate(files_received_total[1m])
```

### Grafana (Dashboards)

**Access**: http://localhost:3000

**Default Credentials**:
- Username: `admin`
- Password: `admin`

**Setup**:
1. Add Prometheus as data source (URL: `http://prometheus:9090`)
2. Create dashboards for:
   - File transfer throughput
   - Transfer latency percentiles
   - Error rates
   - System resource usage

## Configuration

### Server Configuration

Edit `src/Configs/Config.java`:
```java
public int port = 5050;        // Server port
public String host = "localhost";  // Server hostname
```

### OpenTelemetry Collector

Edit `docker/otel-config/otel-collector.yaml` to modify:
- Receivers (OTLP endpoints)
- Processors (batch, memory limiter)
- Exporters (Jaeger, Prometheus, debug)

### Prometheus

Edit `docker/prometheus/prometheus.yml` to:
- Adjust scrape intervals
- Add additional scrape targets
- Configure retention policies

## Performance Analysis

### Metrics to Monitor

1. **Latency**
   - End-to-end file transfer time
   - Compression/decompression time
   - Network transfer time

2. **Throughput**
   - Files per second
   - Bytes per second
   - Compression ratio

3. **Error Rates**
   - Checksum mismatches
   - Connection failures
   - File write errors

4. **Tracing Overhead**
   - Compare performance with tracing enabled/disabled
   - Measure sampling impact on system responsiveness

### Experimental Analysis

To analyze performance:

1. **Run with AlwaysOn sampling**:
   ```bash
   -Dotel.traces.sampler=always_on
   ```
   - Measure latency, throughput, CPU usage
   - Collect trace data

2. **Run with Probability sampling (20%)**:
   ```bash
   -Dotel.traces.sampler=traceidratio
   -Dotel.traces.sampler.arg=0.2
   ```
   - Compare metrics with AlwaysOn
   - Analyze trace coverage

3. **Run with tracing disabled**:
   - Remove `-javaagent` flag
   - Measure baseline performance
   - Compare overhead

### Expected Findings

- **Compression**: Reduces transfer time for text/compressible files
- **Chunking**: Enables handling of large files without memory issues
- **Checksums**: Minimal overhead, ensures data integrity
- **Sampling**: 20% sampling reduces trace volume by 80% with minimal information loss
- **Tracing Overhead**: Typically 1-5% performance impact

## Troubleshooting

### Common Issues

**1. Connection Refused**
- Ensure server is running before starting client
- Check port 5050 is not in use
- Verify firewall settings

**2. No Traces in Jaeger**
- Verify OTLP collector is running: `docker ps`
- Check collector logs: `docker logs otel-collector`
- Ensure `-Dotel.exporter.otlp.endpoint` is correct
- Verify sampling configuration

**3. Metrics Not Appearing in Prometheus**
- Check Prometheus targets: http://localhost:9090/targets
- Verify OTLP collector is exporting to Prometheus
- Check Prometheus configuration file

**4. Checksum Mismatches**
- Indicates data corruption during transfer
- Check network stability
- Verify compression/decompression logic
- Review error logs

**5. Out of Memory Errors**
- Reduce file sizes in test data
- Increase JVM heap size: `-Xmx2g`
- Check chunk size (currently 8KB)

### Debugging Tips

1. **Enable Debug Logging**:
   ```bash
   -Dotel.javaagent.debug=true
   ```

2. **Check Collector Logs**:
   ```bash
   docker logs otel-collector
   ```

3. **Verify Network Connectivity**:
   ```bash
   telnet localhost 5050  # Server port
   telnet localhost 4317  # OTLP collector
   ```

4. **View Application Logs**:
   - Client and server print detailed logs to console
   - Check for exception stack traces
   - Monitor span events in logs

## Dependencies

### OpenTelemetry Libraries

All JARs are included in `src/lib/`:
- `opentelemetry-api-1.41.0.jar`
- `opentelemetry-sdk-1.41.0.jar`
- `opentelemetry-exporter-otlp-common-1.41.0.jar`
- `opentelemetry-exporter-sender-okhttp-1.41.0.jar`
- And other required dependencies

### Java Agent

- `opentelemetry-javaagent.jar` (in `src/otel/`)

---

## Quick Reference

### Start Everything
```bash
# Terminal 1: Start observability stack
cd docker && docker compose up -d

# Terminal 2: Start server
cd src
java -javaagent:./otel/opentelemetry-javaagent.jar \
     -Dotel.service.name=file-server \
     -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -Dotel.exporter.otlp.protocol=grpc \
     -Dotel.metrics.exporter=otlp \
     -Dotel.traces.sampler=always_on \
     -cp ".:lib/*:otel/*" server.FileServer

# Terminal 3: Run client
java -javaagent:./otel/opentelemetry-javaagent.jar \
     -Dotel.service.name=file-client \
     -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -Dotel.exporter.otlp.protocol=grpc \
     -Dotel.metrics.exporter=otlp \
     -Dotel.traces.sampler=always_on \
     -cp ".:lib/*:otel/*" client.ClientRunner
```

### Access Dashboards
- **Jaeger**: http://localhost:16686
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)
