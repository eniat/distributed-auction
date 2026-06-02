package frontend;

import io.grpc.stub.StreamObserver;
import common.*;
import replica.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;

import java.util.*;

public class FrontEndImpl extends AuctionServiceGrpc.AuctionServiceImplBase implements FrontEndAdmin {

    //TODO:
    // Add state variables
    private volatile String sequencerName = null;
    private final List<String> memberNames = Collections.synchronizedList(new java.util.ArrayList<>());

    // === FrontEndAdmin ===
    @Override 
    public String getCurrentSequencerName() throws RemoteException 
    { 
        return sequencerName; 
    }

    @Override 
    public void registerReplica(int id, String rmiName) throws RemoteException {
        //TODO:
        // Add the new member to the list of members
        memberNames.add(rmiName);
        // If no sequencer (leader) assigned, make the first one to register the sequencer by calling setSequencer(true) on the replica
        if (sequencerName == null) {
            sequencerName = rmiName;
            try {
                ReplicatedAuction replica = lookup(rmiName);
                replica.setSequencer(true);
            } catch (Exception e) {
                throw new RemoteException("Failed to add " + rmiName, e);
            }
        }

        System.out.println("Registered replica " + rmiName + "; leader=" + sequencerName);
    }

    @Override
    public void getSpec(GetSpecRequest req, StreamObserver<Item> resp) {
        //TODO:
        // Call getSpec directly on the current sequencer
        try {
            // Call auction.getSpec(itemId) on the RMI server.
            AuctionItem ai = lookupLeader().getSpec(req.getItemId());
            // If null, return an empty Item message.
            if (ai == null) {
                resp.onNext(Item.newBuilder().build());
                resp.onCompleted();
            } else {
                // Otherwise, map fields to a gRPC Item and return it.
                Item item = Item.newBuilder().setItemId(ai.itemID).setName(ai.name).setDescription(ai.description)
                        .setReservePrice(ai.reservePrice).setHighestBid(ai.highestBid).build();
                resp.onNext(item);
                resp.onCompleted();
            }

        } catch (Exception first) {
            // Handle any errors (you may need to elect a new leader if the current one has crashed) 
            try {
                // I suggest you implement leader election in the skeleton method provided below (electNewLeader)
                electNewLeader();
                // NOTE: if you elect a new leader, you have to call getSpec on the new leader
                AuctionItem ai = lookupLeader().getSpec(req.getItemId());
                // If null, return an empty Item message.
                if (ai == null) {
                    resp.onNext(Item.newBuilder().build());
                    resp.onCompleted();
                } else {
                    // Otherwise, map fields to a gRPC Item and return it.
                    Item item = Item.newBuilder().setItemId(ai.itemID).setName(ai.name).setDescription(ai.description)
                            .setReservePrice(ai.reservePrice).setHighestBid(ai.highestBid).build();
                    resp.onNext(item);
                    resp.onCompleted();
                }

            } catch (Exception second) {resp.onError(second);}
        }  
    }


    // ===== gRPC: READS (direct to leader's Auction API) =====
    @Override
    public void listItems(Empty req, StreamObserver<ListReply> resp) {
        //TODO:
        //Call listItems on the current sequencer
        try{
            // Call auction.listItems() on the RMI server.
            AuctionItem[] items = lookupLeader().listItems();
            ListReply.Builder replyBuilder = ListReply.newBuilder();
            // Map each AuctionItem to the gRPC Item message.
            for (AuctionItem ai : items) {
                Item item = Item.newBuilder().setItemId(ai.itemID).setName(ai.name).setDescription(ai.description)
                        .setReservePrice(ai.reservePrice).setHighestBid(ai.highestBid).build();
                        replyBuilder.addItems(item);
            }
            // Build and return a ListReply containing all items.
            resp.onNext(replyBuilder.build());
            resp.onCompleted();
        } catch (Exception first) {
            // Handle any errors (you may need to elect a new leader if the current one has crashed) 
            try{
                // I suggest you implement leader election in the skeleton method provided below (electNewLeader)
                electNewLeader();
                // NOTE: if you elect a new leader, you have to call listItems on the new leader
                AuctionItem[] items = lookupLeader().listItems();
                ListReply.Builder replyBuilder = ListReply.newBuilder();
                // Map each AuctionItem to the gRPC Item message.
                for (AuctionItem ai : items) {
                    Item item = Item.newBuilder().setItemId(ai.itemID).setName(ai.name).setDescription(ai.description)
                            .setReservePrice(ai.reservePrice).setHighestBid(ai.highestBid).build();
                            replyBuilder.addItems(item);
                }
                // Build and return a ListReply containing all items.
                resp.onNext(replyBuilder.build());
                resp.onCompleted();
            } catch (Exception second) {resp.onError(second);}
        }
    }

    @Override
    public void register(RegisterRequest req, StreamObserver<RegisterReply> resp) {

        //TODO: Suggested (high-level) steps
        // Step 2: Create an Operation object (you can do: op = Operation.register(req.getEmail()))
        Operation op = Operation.register(req.getEmail());

        // Step 1: Lookup the current sequencer (leader)
        try {
            ReplicatedAuction leader = lookupLeader();
            List<String> currentMembers;
            synchronized (memberNames) {
                currentMembers = new ArrayList<>(memberNames);
            }
            // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
            OperationResult result = leader.handleClientOperation(op, currentMembers);
            // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC
            resp.onNext(RegisterReply.newBuilder().setUserId(result.userId).build());
            resp.onCompleted();
        } catch (Exception first ) {
            try {
                // NOTE: you must handle leader failure (elect new one and repeat step 3 on the new leader)
                electNewLeader();
                ReplicatedAuction leader = lookupLeader();
                List<String> currentMembers;
                synchronized (memberNames) {
                    currentMembers = new ArrayList<>(memberNames);
                }
                // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
                OperationResult result = leader.handleClientOperation(op, currentMembers);
                // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC
                resp.onNext(RegisterReply.newBuilder().setUserId(result.userId).build());
                resp.onCompleted();
            } catch (Exception second) {resp.onError(second);}
        }        
    }

    // ===== gRPC: State-mutating calls =====
    @Override
    public void newAuction(NewAuctionRequest req, StreamObserver<NewAuctionReply> resp) {
        //TODO: Suggested (high-level) steps
        // Step 2: Create an Operation object (you can do: op = Operation.newAuction(...))
        Operation op = Operation.newAuction(req.getUserId(), req.getName(), req.getDescription(), req.getReservePrice());
        // Step 1: Lookup the current sequencer (leader)
        try {
            ReplicatedAuction leader = lookupLeader();
            List<String> currentMembers;
            synchronized (memberNames) {
                currentMembers = new ArrayList<>(memberNames);
            }
            // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
            OperationResult result = leader.handleClientOperation(op, currentMembers);
            // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC
            resp.onNext(NewAuctionReply.newBuilder().setItemId(result.itemId).build());
            resp.onCompleted();
        } catch (Exception first) {
            // NOTE: you must handle leader failure (elect new one and repeat step 3 on the new leader)
            try {
                electNewLeader();
                // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
                ReplicatedAuction leader = lookupLeader();
                List<String> currentMembers;
                synchronized (memberNames) {
                    currentMembers = new ArrayList<>(memberNames);
                }
                // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
                OperationResult result = leader.handleClientOperation(op, currentMembers);
                // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC
                resp.onNext(NewAuctionReply.newBuilder().setItemId(result.itemId).build());
                resp.onCompleted();
                } catch (Exception second) {resp.onError(second);}
        }
    }

    @Override
    public void bid(BidRequest req, StreamObserver<BidReply> resp) {
        //TODO: Suggested (high-level) steps
        // Step 2: Create an Operation object (you can do: op = Operation.bid(req.getUserId(), ...))
        Operation op = Operation.bid(req.getUserId(), req.getItemId(), req.getPrice());
        // Step 1: Lookup the current sequencer (leader)
        try {
            ReplicatedAuction leader = lookupLeader();
            List<String> currentMembers;
            synchronized (memberNames) {
                currentMembers = new ArrayList<>(memberNames);
            }
            // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
            OperationResult result = leader.handleClientOperation(op, currentMembers);
            boolean success = result.bidOk;
            // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC
            // Build and return a BidReply with success=true/false.
            resp.onNext(BidReply.newBuilder().setSuccess(success).build());
            resp.onCompleted();
        } catch (Exception first) {
            // NOTE: you must handle leader failure (elect new one and repeat step 3 on the new leader)
            try {
                electNewLeader();
                ReplicatedAuction leader = lookupLeader();
                List<String> currentMembers;
                synchronized (memberNames) {
                    currentMembers = new ArrayList<>(memberNames);
                }
                // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
                OperationResult result = leader.handleClientOperation(op, currentMembers);
                boolean success = result.bidOk;
                // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC
                // Build and return a BidReply with success=true/false.
                resp.onNext(BidReply.newBuilder().setSuccess(success).build());
                resp.onCompleted();
                } catch (Exception second) {resp.onError(second);}
        }
    }

    @Override
    public void closeAuction(CloseRequest req, StreamObserver<AuctionResult> resp) {
        //TODO: Suggested (high-level) steps
        // Step 2: Create an Operation object (you can do: op = Operation.close(req.getUserId(), ...))
        Operation op = Operation.close(req.getUserId(), req.getItemId());
        // Step 1: Lookup the current sequencer (leader)
        try{
            ReplicatedAuction leader = lookupLeader();
            List<String> currentMembers;
            synchronized (memberNames) {
                currentMembers = new ArrayList<>(memberNames);
            }
            // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
            OperationResult result = leader.handleClientOperation(op, currentMembers);
            AuctionResult reply;
            // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC
            if (!result.ok) {
                reply = AuctionResult.newBuilder().setItemId(0).setPrice(0).setWinningUser(0).build();
            } else {
                // Otherwise, map the AuctionResult fields and return them.
                reply = AuctionResult.newBuilder().setItemId(result.closeItem).setWinningUser(result.closeWinner).setPrice(result.closePrice).build();
            }
            resp.onNext(reply);
            resp.onCompleted();
        } catch (Exception first) {
            // NOTE: you must handle leader failure (elect new one and repeat step 3 on the new leader)
            try{
                electNewLeader();
                ReplicatedAuction leader = lookupLeader();
                List<String> currentMembers;
                synchronized (memberNames) {
                    currentMembers = new ArrayList<>(memberNames);
                }
                // Step 3: Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
                OperationResult result = leader.handleClientOperation(op, currentMembers);
                AuctionResult reply;
                // Step 4: Collect OperationResult returned by the call and return it back to the client using gRPC
                if (!result.ok) {
                    reply = AuctionResult.newBuilder().setItemId(0).setPrice(0).setWinningUser(0).build();
                } else {
                    // Otherwise, map the AuctionResult fields and return them.
                    reply = AuctionResult.newBuilder().setItemId(result.closeItem).setWinningUser(result.closeWinner).setPrice(result.closePrice).build();
                }
                resp.onNext(reply);
                resp.onCompleted();
                } catch (Exception second) {resp.onError(second);}
        }
    }

    // I suggest implementing leader election in this method and calling from other methods when needed
    private synchronized void electNewLeader() {
        //TODO:
        String highestReplica = null;
        long highestCommitted = -1;
        // probe all members, pick the replica that reports the highest lastCommitted (if tie → pick any)
        for (String memberName : memberNames) {
            try {
                ReplicatedAuction replica = lookup(memberName);
                long lastCommitted = replica.getLastCommittedSeqNo();
                if (lastCommitted > highestCommitted) {
                    highestCommitted = lastCommitted;
                    highestReplica = memberName;
                }
            } catch (Exception e) {
                // Ignore unreachable
            }
        }
        // Call setSequencer(true) on the selected replica (optionally call setSequencer(false) on the others)
        if (highestReplica == null) {
            sequencerName = null;
            return;
        }
        try {
            ReplicatedAuction newLeader = lookup(highestReplica);
            newLeader.setSequencer(true);
            // Demote others
            for (String memberName : memberNames) {
                if (!memberName.equals(highestReplica)) {
                    try {
                        ReplicatedAuction replica = lookup(memberName);
                        replica.setSequencer(false);
                    } catch (Exception e) {
                        // Ignore unreachable
                    }
                }
            }
        } catch (Exception e) {
            sequencerName = null;
            return;
        }
        sequencerName = highestReplica; 
        System.out.println("Elected new sequencer: " + sequencerName);
    }
    
    // ===== Helpers that may be useful =====
    
    // Looks up and returns a remote reference to the specified replica in the local RMI registry.
    private ReplicatedAuction lookup(String rmiName) throws Exception {
        Registry reg = LocateRegistry.getRegistry();
        return (ReplicatedAuction) reg.lookup(rmiName);
    }

    // Looks up and returns a remote reference to the current sequencer
    private ReplicatedAuction lookupLeader() throws Exception {
        if (sequencerName == null) throw new IllegalStateException("No sequencer set");
        return lookup(sequencerName);
    }

}
