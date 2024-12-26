# SimpleConfig

**SimpleConfig** is a lightweight and flexible Java configuration library that binds static fields in classes to external configuration files, enabling real-time updates and advanced management. It supports dynamic field updates, multiple configuration files, remote synchronization, and integration with third-party libraries.

---

## Key Features

- **Dynamic Field Updates:** Automatically updates static fields after configuration changes with minimal delay (~10 seconds by default).
- **Broad Data Type Support:** Compatible with primitive types, arrays, `List`, `Set`, `Map`, and nested types.
- **Multiple Configuration Files:** Manage multiple configuration files for complex applications.
- **Remote Synchronization:** Keep configurations synchronized with remote servers.
- **Third-Party Support:** Modify private static fields in third-party classes.
- **Action-Based Commands:** Perform various actions like generating, validating, encoding, decoding, and synchronizing configurations via command-line arguments.

---

## Installation

1. Add **SimpleConfig** to your project (via Maven, Gradle, or manually).
2. Define a configuration file (e.g., `server.ini`) and bind it to your application's configuration class.

---

## Usage Guide
### 1. Define a Configuration Class

Create a Java class with static fields to store your application's configuration values:

```java
public class PiledConfig {
    public static int coreThreads = 20;
    public static int maxThreads = 128;
    public static int idleThreads = 5;
    public static int threadIdleSeconds = 60;
    public static int maxQueueRequests = 1000;
}
```

### 2. Use Static Fields in Your Application

Access static fields directly to configure your application logic:

```java
workerPool = new SimpleThreadPoolExecutor(
    PiledConfig.coreThreads,
    PiledConfig.maxThreads <= 0 ? Integer.MAX_VALUE : PiledConfig.maxThreads,
    Math.max(PiledConfig.idleThreads, 1),
    PiledConfig.threadIdleSeconds,
    TimeUnit.SECONDS,
    PiledConfig.maxQueueRequests,
    new SimpleNamedThreadFactory("Worker Pool")
);

if (lastCoreThreads != PiledConfig.coreThreads) {
    workerPool.setCorePoolSize(PiledConfig.coreThreads);
}
if (lastMaxThreads != PiledConfig.maxThreads) {
    workerPool.setMaximumPoolSize(PiledConfig.maxThreads);
}
```

### 3. Initialize Configuration on Startup

Bind your configuration class to a configuration file during application startup:
```java
	public static void main(String[] args) {
		args = Config.initialize(args); // Remove known configuration argument items and return left arguments
		Config.register(PiledConfig.class);
		// Application initialization logic...
	}
```
Or using --run:wrapper to run your application without modifying your existing code. See **5. Command-Line Actions**.

### 4. Edit Configuration File

Define your configuration values in an external file (e.g., server.ini):
```ini
# PiledConfig
coreThreads=20
maxThreads=128
idleThreads=5
threadIdleSeconds=60
maxQueueRequests=1000
```

### 5. Command-Line Actions

You can perform various actions using command-line arguments with the `--run:<action>` flag:

- **`usage`**: Print the usage instructions.
- **`generator`**: Generate default configuration files.
- **`encoder`**: Encode a sensitive string (e.g., passwords).
- **`decoder`**: Decode an encoded string back to its original value.
- **`validator`**: Validate the structure and contents of configuration files.
- **`synchronizer`**: Synchronize configuration files with remote servers.
- **`wrapper`**: Dynamically run another application's main method while inheriting its configuration.

#### Example Command:

```bash
java -jar myapp.jar im.webuzz.config.Config --run:usage

java -jar myapp.jar im.webuzz.config.Config ./server.ini --run:generator

java -jar myapp.jar im.webuzz.config.Config --config:app.name=CRM ./server.ini --run:wrapper com.company.CRMApplication
```

## Advanced Features

- **File Change Detection:** Automatically detects changes in the configuration file and applies updates without requiring application restarts.
- **Nested Configuration Support:** Handles nested types and complex structures in configuration classes.
- **Dynamic Command Execution:** Use the `wrapper` action to execute the main method of any Java class with the existing configuration.

---

## License

This library is distributed under the **Eclipse Public License 1.0**, which allows for both commercial and non-commercial usage.

---

## Contributing

We welcome contributions! Feel free to submit issues, feature requests, or pull requests to improve **SimpleConfig**.

---

With **SimpleConfig**, streamline your application's configuration management and focus on building amazing software! 🚀
