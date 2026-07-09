# Distributed Auction System

A Java distributed auction platform using a gRPC client-facing API and Java RMI for back-end communication. The repository contains two implementations: `part1/`, a single-server auction system, and `part2/`, a replicated auction system with sequencer-based ordering, majority commits and leader failover.

The project demonstrates remote communication, service interfaces, state replication, quorum-based commits and basic fault tolerance in a distributed system.

## Features

- User registration, auction creation, bidding, item listing, item inspection and auction closure.
- Reserve-price and highest-bid validation.
- Auction closure restricted to the item creator.
- gRPC API exposed to clients.
- Java RMI used for internal communication.
- Replicated implementation with five local replicas.
- Sequencer-based ordering for state-changing operations.
- Majority acknowledgement before commit.
- Basic leader failover when the current sequencer is unreachable.
- Scripted local startup and cleanup.

## Tech Stack

Java 17, Maven, gRPC, Protocol Buffers, Java RMI, Bash and Makefile.

## Repository Structure

```text
.
├── part1/
│   ├── proto/auction.proto
│   ├── src/main/java/client/
│   ├── src/main/java/frontend/
│   ├── src/main/java/server/
│   ├── pom.xml
│   └── server.sh
└── part2/
    ├── proto/auction.proto
    ├── src/main/java/client/
    ├── src/main/java/common/
    ├── src/main/java/frontend/
    ├── src/main/java/replica/
    ├── pom.xml
    └── server.sh
```

## Architecture

```text
Client -> gRPC Front End -> Java RMI Auction Server / Replicas
```

The client talks only to the gRPC front end. In `part1`, the front end forwards requests to one RMI auction server. In `part2`, the front end forwards state-changing requests to the current sequencer replica.

## Part 1: Single-Server Version

`part1/` implements the baseline auction service.

Main components:

- `AuctionClient.java` — sample client and test runner.
- `FrontEndServer.java` — starts the gRPC service on port `50055`.
- `FrontEndImpl.java` — maps gRPC calls to RMI calls.
- `Auction.java` — RMI auction interface.
- `AuctionImpl.java` — in-memory auction logic.
- `ServerMain.java` — binds the auction server to the RMI registry.

The server tracks users, active items, item owners, highest bidders and closed auctions.

## Part 2: Replicated Version

`part2/` extends the system with replicated auction servers.

Main components:

- `FrontEndImpl.java` — tracks replicas, forwards writes and handles failover.
- `FrontEndAdmin.java` — RMI admin interface used by replicas.
- `ReplicaMain.java` — starts and registers each replica.
- `ReplicaImpl.java` — replicated auction state machine.
- `ReplicatedAuction.java` — RMI replica interface.
- `Operation.java` — serialisable state-changing operation.
- `LogEntry.java` — ordered replicated log entry.
- `OperationResult.java` — typed result returned after applying an operation.

State-changing actions are converted into operations, assigned sequence numbers, replicated to other replicas and committed after a majority acknowledgement.

## Supported API Operations

The gRPC API supports `Register`, `NewAuction`, `Bid`, `ListItems`, `GetSpec` and `CloseAuction`.

## Replication Model

The replicated version uses a sequencer-based design. The sequencer assigns a sequence number to each write operation, proposes it to the other replicas, applies it after majority acknowledgement, then tells replicas to commit up to the same sequence number. Read-only operations are served by the current sequencer.

## Leader Failover

If the current sequencer is unreachable, the front end probes available replicas and elects a reachable replica with the highest committed sequence number. The selected replica is promoted to sequencer and the original request is retried.

## Prerequisites

Java 17+, Maven 3.8+, Linux/macOS shell or WSL, port `1099` for RMI and port `50055` for gRPC.

## Running Part 1

```bash
cd part1
chmod +x server.sh
./server.sh
```

Run the sample client:

```bash
mvn -q exec:java -Dexec.mainClass=client.AuctionClient
```

Stop and clean with `make clean`.

## Running Part 2

```bash
cd part2
chmod +x server.sh
./server.sh
```

This starts one RMI registry, one gRPC front end and five replicas.

Run the sample client:

```bash
mvn -q exec:java -Dexec.mainClass=client.AuctionClient
```

Stop and clean with `make clean`.

## Testing

The included client registers users, creates auctions, places valid and invalid bids, lists active items, retrieves item details, closes auctions and checks edge cases such as low bids, missing items and unauthorised closure.

For replication testing, run `part2/`, stop one replica process manually and rerun client operations to check whether the system continues with the remaining replicas.

## Notes

This is a local distributed-systems prototype. State is held in memory, so data is lost when processes stop. The replication logic covers ordered writes, majority commits, replica registration and basic failover, but not persistent storage, authentication, encrypted transport, Byzantine fault tolerance or production-grade consensus.

## What This Demonstrates

Java distributed systems development, gRPC service design, Java RMI communication, client/front-end/server separation, in-memory auction logic, replicated operation logs, majority-based commit handling and basic leader election.

## Usage Notice

This repository is provided for portfolio and review purposes only.

All rights are reserved. No permission is granted to copy, redistribute, submit, or reuse this work, in whole or in part, for academic coursework, assessment, or commercial purposes.

Where this repository relates to university coursework, it is shared only to demonstrate my own technical work and should not be used by other students as a submission or solution.

