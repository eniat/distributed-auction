# Part 2 (Replicated Auction Service)

This project extends the single-server implementation by deploying a replicated auction service behind a gRPC front-end. Five Auction Server replicas are started automatically and communicate using Java RMI.

Processes are started via `server.sh`. Runtime PID files are created automatically during execution and can be removed using `make clean`.

## Prerequisites

- Java 17+
- Maven 3.8+
- Linux shell
- Port **1099** available for `rmiregistry`
- Port **50055** available for the gRPC FrontEnd

## Build & Run

```bash
./server.sh
```

Running `server.sh` will:

- Build the project using Maven (`mvn clean package`)
- Start `rmiregistry` on port **1099**
- Launch the gRPC FrontEnd
- Launch **five replicated Auction Server instances**
- Remove any previously running processes before startup

The system is ready within approximately 10 seconds.

## Stop and Clean

```bash
make clean
```

This command:

- Stops all running replicas
- Stops the FrontEnd
- Stops `rmiregistry`
- Removes temporary PID files
- Executes `mvn clean`

## Troubleshooting

### Address already in use

If a previous instance is still running, execute:

```bash
make clean
```

or terminate the remaining processes manually before restarting.
