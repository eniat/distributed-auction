package frontend;

import io.grpc.stub.StreamObserver;
import common.*;
import replica.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;

import java.util.*;

public class FrontEndImpl extends AuctionServiceGrpc.AuctionServiceImplBase implements FrontEndAdmin {

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
                electNewLeader();
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
                electNewLeader();
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

        // Create an Operation object (you can do: op = Operation.register(req.getEmail()))
        Operation op = Operation.register(req.getEmail());

        // Lookup the current sequencer (leader)
        try {
            ReplicatedAuction leader = lookupLeader();
            List<String> currentMembers;
            synchronized (memberNames) {
                currentMembers = new ArrayList<>(memberNames);
            }
            // Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
            OperationResult result = leader.handleClientOperation(op, currentMembers);
            // Collect OperationResult returned by the call and return it back to the client using gRPC
            resp.onNext(RegisterReply.newBuilder().setUserId(result.userId).build());
            resp.onCompleted();
        } catch (Exception first ) {
            try {
                electNewLeader();
                ReplicatedAuction leader = lookupLeader();
                List<String> currentMembers;
                synchronized (memberNames) {
                    currentMembers = new ArrayList<>(memberNames);
                }
                // Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
                OperationResult result = leader.handleClientOperation(op, currentMembers);
                // Collect OperationResult returned by the call and return it back to the client using gRPC
                resp.onNext(RegisterReply.newBuilder().setUserId(result.userId).build());
                resp.onCompleted();
            } catch (Exception second) {resp.onError(second);}
        }        
    }

    // ===== gRPC: State-mutating calls =====
    @Override
    public void newAuction(NewAuctionRequest req, StreamObserver<NewAuctionReply> resp) {
        // Create an Operation object (you can do: op = Operation.newAuction(...))
        Operation op = Operation.newAuction(req.getUserId(), req.getName(), req.getDescription(), req.getReservePrice());
        // Lookup the current sequencer (leader)
        try {
            ReplicatedAuction leader = lookupLeader();
            List<String> currentMembers;
            synchronized (memberNames) {
                currentMembers = new ArrayList<>(memberNames);
            }
            // Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
            OperationResult result = leader.handleClientOperation(op, currentMembers);
            // Collect OperationResult returned by the call and return it back to the client using gRPC
            resp.onNext(NewAuctionReply.newBuilder().setItemId(result.itemId).build());
            resp.onCompleted();
        } catch (Exception first) {
            try {
                electNewLeader();
                // Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
                ReplicatedAuction leader = lookupLeader();
                List<String> currentMembers;
                synchronized (memberNames) {
                    currentMembers = new ArrayList<>(memberNames);
                }
                // Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
                OperationResult result = leader.handleClientOperation(op, currentMembers);
                // Collect OperationResult returned by the call and return it back to the client using gRPC
                resp.onNext(NewAuctionReply.newBuilder().setItemId(result.itemId).build());
                resp.onCompleted();
                } catch (Exception second) {resp.onError(second);}
        }
    }

    @Override
    public void bid(BidRequest req, StreamObserver<BidReply> resp) {
        // Create an Operation object (you can do: op = Operation.bid(req.getUserId(), ...))
        Operation op = Operation.bid(req.getUserId(), req.getItemId(), req.getPrice());
        // Lookup the current sequencer (leader)
        try {
            ReplicatedAuction leader = lookupLeader();
            List<String> currentMembers;
            synchronized (memberNames) {
                currentMembers = new ArrayList<>(memberNames);
            }
            // Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
            OperationResult result = leader.handleClientOperation(op, currentMembers);
            boolean success = result.bidOk;
            // Collect OperationResult returned by the call and return it back to the client using gRPC
            // Build and return a BidReply with success=true/false.
            resp.onNext(BidReply.newBuilder().setSuccess(success).build());
            resp.onCompleted();
        } catch (Exception first) {
            try {
                electNewLeader();
                ReplicatedAuction leader = lookupLeader();
                List<String> currentMembers;
                synchronized (memberNames) {
                    currentMembers = new ArrayList<>(memberNames);
                }
                // Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
                OperationResult result = leader.handleClientOperation(op, currentMembers);
                boolean success = result.bidOk;
                // Collect OperationResult returned by the call and return it back to the client using gRPC
                // Build and return a BidReply with success=true/false.
                resp.onNext(BidReply.newBuilder().setSuccess(success).build());
                resp.onCompleted();
                } catch (Exception second) {resp.onError(second);}
        }
    }

    @Override
    public void closeAuction(CloseRequest req, StreamObserver<AuctionResult> resp) {
        // Create an Operation object (you can do: op = Operation.close(req.getUserId(), ...))
        Operation op = Operation.close(req.getUserId(), req.getItemId());
        // Lookup the current sequencer (leader)
        try{
            ReplicatedAuction leader = lookupLeader();
            List<String> currentMembers;
            synchronized (memberNames) {
                currentMembers = new ArrayList<>(memberNames);
            }
            // Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
            OperationResult result = leader.handleClientOperation(op, currentMembers);
            AuctionResult reply;
            // Collect OperationResult returned by the call and return it back to the client using gRPC
            if (!result.ok) {
                reply = AuctionResult.newBuilder().setItemId(0).setPrice(0).setWinningUser(0).build();
            } else {
                // Otherwise, map the AuctionResult fields and return them.
                reply = AuctionResult.newBuilder().setItemId(result.closeItem).setWinningUser(result.closeWinner).setPrice(result.closePrice).build();
            }
            resp.onNext(reply);
            resp.onCompleted();
        } catch (Exception first) {
            try{
                electNewLeader();
                ReplicatedAuction leader = lookupLeader();
                List<String> currentMembers;
                synchronized (memberNames) {
                    currentMembers = new ArrayList<>(memberNames);
                }
                // Call the handleClientOperation on the leader, passing the operation and current list of members (including leader)
                OperationResult result = leader.handleClientOperation(op, currentMembers);
                AuctionResult reply;
                // Collect OperationResult returned by the call and return it back to the client using gRPC
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

    private synchronized void electNewLeader() {
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
        // Call setSequencer(true) on the selected replica
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
