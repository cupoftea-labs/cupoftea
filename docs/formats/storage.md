# Cloud Object Storage (S3, GCS, Azure Blob, HTTP)

The `cupoftea-storage` module provides direct integration with Cloud Object Storage systems and HTTP endpoints, allowing seamless reading and writing of DataFrames without downloading multi-gigabyte files locally.

---

## 🚀 Key Capabilities

- **Unified Cloud URI Resolution**: Direct support for standard storage URIs (`s3://`, `gs://`, `abfss://`, `https://`).
- **Selective Parquet Byte-Range Reads**: Reads remote Parquet footers and metadata selectively using `RFC 7233 / 9110 HTTP Range Requests` via `SeekableByteChannel`, avoiding full-file transfers.
- **Streaming Egress**: Stream data directly to cloud object stores on `df.write().csv(...)`, `df.write().parquet(...)`, etc.
- **Pluggable SPI Architecture**: Extensible `StorageFileSystem` provider interface discovered via `java.util.ServiceLoader`.
- **Local Testing & Mocking**: Built-in in-memory object simulation and endpoint overrides for MinIO, LocalStack, Azurite, and fake-gcs-server.

---

## 📦 Supported Schemes

| Provider | URI Schemes | Description |
| :--- | :--- | :--- |
| **Amazon S3** | `s3://`, `s3a://`, `s3n://` | AWS S3 buckets and S3-compatible stores (e.g., MinIO, Ceph). |
| **Google Cloud Storage** | `gs://`, `gcs://` | Google Cloud Storage buckets. |
| **Azure Blob / ADLS Gen2** | `abfs://`, `abfss://`, `wasb://`, `wasbs://` | Azure Blob Storage & Azure Data Lake Storage Gen2. |
| **HTTP / HTTPS** | `http://`, `https://` | Web servers and pre-signed cloud download URLs with byte-range requests. |

---

## 💻 Installation

Add `cupoftea-storage` alongside `cupoftea-core` and your desired format modules:

### Maven
```xml
<dependency>
    <groupId>io.github.cupoftea-labs</groupId>
    <artifactId>cupoftea-storage</artifactId>
    <version>0.29.0</version>
</dependency>
```

### Gradle
```groovy
implementation 'io.github.cupoftea-labs:cupoftea-storage:0.29.0'
```

---

## 📖 Usage Examples

### 1. Reading from Cloud Object Storage

```java
import com.cupoftea.CupOfTea;

// Read CSV directly from S3
CupOfTea s3Df = CupOfTea.read().csv("s3://analytics-bucket/raw/data.csv");

// Read Parquet with selective byte-range seeks from Google Cloud Storage
CupOfTea gcsDf = CupOfTea.read().parquet("gs://data-lake/transactions/year=2026/data.parquet");

// Read from an Azure Blob Storage container
CupOfTea azureDf = CupOfTea.read().csv("abfss://sales@account.dfs.core.windows.net/orders.csv");

// Read from a pre-signed HTTPS URL with random-access range requests
CupOfTea httpDf = CupOfTea.read().parquet("https://storage.googleapis.com/public-datasets/sample.parquet");
```

### 2. Writing to Cloud Object Storage

```java
CupOfTea df = CupOfTea.read().records(users);

// Stream CSV to Amazon S3
df.write().csv("s3://analytics-bucket/exports/users.csv");

// Export to Google Cloud Storage
df.write().csv("gs://data-lake/exports/users.csv");
```

### 3. Local Simulation & MinIO / LocalStack Testing

For integration testing or local dev setups (such as MinIO or Azurite), you can override endpoints or populate mock objects:

```java
import com.cupoftea.storage.cloud.CloudStorageFileSystem;

// Point S3 requests to a local MinIO instance
CloudStorageFileSystem.setEndpointOverride("s3", "http://localhost:9000");

// Or clear simulated objects after unit tests
CloudStorageFileSystem.clearMockObjects();
```

---

## 🧩 Implementing a Custom StorageFileSystem

To add support for a custom storage protocol (e.g. SFTP, HDFS, or proprietary APIs), implement `StorageFileSystem` and register it in `META-INF/services/com.cupoftea.storage.StorageFileSystem`:

```java
package my.company.storage;

import com.cupoftea.storage.StorageFileSystem;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;

public class SftpStorageFileSystem implements StorageFileSystem {

    @Override
    public boolean supports(String scheme) {
        return "sftp".equalsIgnoreCase(scheme);
    }

    @Override
    public InputStream openInputStream(URI uri) { /* ... */ }

    @Override
    public OutputStream openOutputStream(URI uri) { /* ... */ }

    @Override
    public SeekableByteChannel openSeekable(URI uri) { /* ... */ }

    @Override
    public long size(URI uri) { /* ... */ }

    @Override
    public boolean exists(URI uri) { /* ... */ }
}
```
