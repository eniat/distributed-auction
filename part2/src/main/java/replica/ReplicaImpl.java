package replica;

import common.*;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplicaImpl extends UnicastRemoteObject implements ReplicatedAuction {

    // ----- sequencing & log state (I suggest you keep these and use them) -----
    private boolean isLeader = false;
    private long lastSeqAssigned = 0; // This is used by the leader to assign a seq number to each Operation      
    private long lastCommitted = 0;           
    private long lastApplied = 0;             
    private TreeMap<Long, LogEntry> log = new TreeMap<>(); 

    // ----- state machine (in-memory) -----
    //TODO: declare/initialise state variables 
    private final Map<Integer, String> users = new ConcurrentHashMap<>();
    private final Map<Integer, AuctionItem> items = new ConcurrentHashMap<>();
    private final Set<Integer> closedItems = Collections.synchronizedSet(new HashSet<>());
    private final Map<Integer, Integer> itemOwners = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> highestBidders = new ConcurrentHashMap<>();
    private final AtomicInteger userIdCounter = new AtomicInteger(1);
    private final AtomicInteger itemIdCounter= new AtomicInteger(1);

    // ----- replica ID and name (keep these) ----
    private final int id;
    private final String myName;   
    public ReplicaImpl(int id, String rmiName) throws RemoteException { this.id = id; this.myName = rmiName; }

    // ================= Auction (read-only calls are executed locally) =================
    @Override 
    public AuctionItem getSpec(int itemID) { 
        //TODO
        AuctionItem item = items.get(itemID);
        if (item == null) {
            return null;
        }
        return item;
    }

    @Override 
    public AuctionItem[] listItems() 
    { 
        //TODO: 
        Collection<AuctionItem> vals = items.values();
        return vals.toArray(new AuctionItem[0]);
    }

    // NOTE: this is now a local function that may be used to update local state 
    private int newAuction(int userID, AuctionSaleItem item)
    {
        //TODO
        // - If userID not registered, return -1 
        if (!users.containsKey(userID)) {
            return -1;
        }
        // - Create a new itemID 
        int newItemId = itemIdCounter.getAndIncrement();
        // - Store AuctionItem with initial highestBid = 0
        AuctionItem ai = new AuctionItem(newItemId, item.name, item.description, item.reservePrice);
        // - Record itemOwner[itemID] = userID
        items.put(newItemId, ai);
        itemOwners.put(newItemId, userID);
        // - Return itemID
        return newItemId;
    }

    // NOTE: this is now a local function that may be used to update local state 
    private AuctionResult closeAuction(int userID, int itemID){
        //TODO
        // - Look up item; if missing, return null.
        AuctionItem item = items.get(itemID);
        if (item == null) {
            return null;
        }
        // - Check owner: only creator can close; if not owner, return null.
        Integer ownerId = itemOwners.get(itemID);
        if (ownerId == null || ownerId != userID) {
            return null;
        }
        // - Mark item as closed (add to closedItems) and remove from active items map.
        if (closedItems.contains(itemID)) {
            return null;
        }
        closedItems.add(itemID);
        items.remove(itemID);
        // - Return AuctionResult(itemID, winningUser=userID, price=highestBid).
        Integer winningUserId = highestBidders.get(itemID);
        if (winningUserId != null) {
            return new AuctionResult(itemID, winningUserId, item.highestBid);
        }
        // If no bids placed
        return new AuctionResult(itemID, 0, 0);
    }
    // NOTE: this is now a local function that may be used to update local state 
    private boolean bid(int userID, int itemID, int price){
        //TODO
        // - If item missing OR user unknown OR item already closed -> return false.
        if (!items.containsKey(itemID) || !users.containsKey(userID) || closedItems.contains(itemID)) {
            return false;
        }
        // - If price > current highestBid AND price >= reservePrice:
        AuctionItem item = items.get(itemID);
        if (price > item.highestBid && price >= item.reservePrice) {
        //     - Update highestBid and return true.
            item.highestBid=price;
            highestBidders.put(itemID, userID);
            return true;
        }
        // - Otherwise, return false to indicate unsuccessful bid.
        return false;
    }
    // NOTE: this is now a local function that may be used to update local state 
    private int register(String email) {
        //TODO
        // - Allocate a new userID
        int newUserId = userIdCounter.getAndIncrement();
        users.put(newUserId, email);   
        // - Record mapping userID -> email
        // - Return the new userID
        return newUserId;
    }
    

    // ================= Replication core =================
    @Override
    public OperationResult handleClientOperation(Operation op, List<String> memberList) throws RemoteException {
        if (!isLeader) {
            //This should not happen
            return OperationResult.fail("Not leader");
        }
        //TODO (suggested high-level steps):
        // Step 1: Add the operation to the local log
        long seqNo = ++lastSeqAssigned;
        log.put(seqNo, new LogEntry(seqNo, op));
        // Step 2: Propose the operation to the rest of the replicas in the memberList (i.e., call propose remote method on members *excluding self* and ignore any unreachable replicas)
        int ackCount = 1;
        for (String replicaName : memberList) {
            if (replicaName.equals(this.myName)) {
                continue; // skip self
            }
            try{
                // Propose and increment ackCount if successful
                ReplicatedAuction rep = lookup(replicaName);
                rep.propose(seqNo, op);
                ackCount++;
        } catch (Exception e){} 
    }
        // Step 3: If majority of replicas acknowledges (assume leader acks), then:
        // Step 4: If a majority quorum is not achieved (or in case of other errors), return OperationResult.fail("") and provide a description of the error in the fail() method
        if (ackCount < majority(memberList.size())){
            return OperationResult.fail("Not enough acks");
        }
           // Step 3.1: Locally execute the operation on self (as the leader) - you may use apply(), set the operation in the log as committed, and update any other state variables, if needed
        OperationResult result = apply(op);
        LogEntry logEntry = log.get(seqNo);
        logEntry.committed = true;
        lastCommitted = seqNo;
        lastApplied = seqNo;
           //Step 3.2 call commitUpTo on all replicas, again ignoring any unreachable replicas) 
        for (String replicaName : memberList) {
            if (replicaName.equals(this.myName)) {
                continue; // skip self
            }
            try{
                ReplicatedAuction rep = lookup(replicaName);
                rep.commitUpTo(seqNo);
        } catch (Exception e){} 
        }
        return result;
    }

    // Helper method to compute the required number of replicas to achieve majority given the number of the members
    private int majority(int n){ 

        return (n/2)+1; 

    }

    @Override
    public boolean propose(long seqNo, Operation op) {
        // Add the operation to the local Log
        LogEntry existing = log.get(seqNo);
        if (existing == null || !existing.committed) {
            log.put(seqNo, new LogEntry(seqNo, op)); 
        }

        return true;
    }

    @Override
    public boolean commitUpTo(long seqNo) {

        //TODO (suggested high-level steps):
        // Step 1: Commit the local log entries upto seqNo if there are no missing log entries
        if (seqNo <= lastCommitted) {
            return true; // up to date
        }
        try{
            long nextToCommit = lastCommitted + 1;
            // Step 2: If there are missing entries before seqNo in the local log, pull them from the leader (using getEntriesAfter)
            boolean missingEntries = false;
            for (long i = nextToCommit; i <= seqNo; i++) {
                if (!log.containsKey(i)) {
                    missingEntries = true;
                    break;
                }
            }
            // if there are missing entries fetch from leader and fill in
            if (missingEntries) {
                String leaderName = findLeaderName();
                ReplicatedAuction leader = lookup(leaderName);
                List<LogEntry> entries = leader.getEntriesAfter(lastCommitted);
                for (LogEntry ent : entries) {
                    LogEntry local = log.get(ent.seqNo);
                    if (local == null) {
                        local = new LogEntry(ent.seqNo, ent.op );
                        log.put(ent.seqNo, local);
                    }
                    local.committed = true;
                }
            }
            // Step 3: Execute and commit the new operation(s) - you may use apply() on each operation
            for (long i = lastCommitted+1; i <= seqNo; i++) {
                LogEntry entry = log.get(i);
                if (entry == null) {
                    return false; // still missing
                }
                if (!entry.committed) {
                    entry.committed = true;
                } 
                // Apply operations in order
                if (i > lastApplied) {
                    apply(entry.op);
                    lastApplied = i;
                    
                }
                lastCommitted = i;
            }
            return true;
        } catch (Exception e) {
            return false;  
        }
    }

    // You may use this function to apply operations on the local state
    private OperationResult apply(Operation op){
        switch (op.type) {
            case REGISTER -> {
                int uid = register(op.email);  
                return OperationResult.reg(uid);
            }
            case NEW_AUCTION -> {
                int iid = newAuction(op.userId, new AuctionSaleItem(op.name, op.description, op.reservePrice));
                return (iid > 0) ? OperationResult.newA(iid) : OperationResult.fail("unknown user");
            }
            case BID -> {
                boolean ok = bid(op.userId, op.itemId, op.reservePrice);
                return OperationResult.bid(ok);
            }
            case CLOSE -> {
                AuctionResult ar = closeAuction(op.userId, op.itemId); // null if not owner
                if (ar == null) 
                    return OperationResult.fail("Auction close not permitted!");
                return OperationResult.close(ar.itemID, ar.winningUser, ar.price);
            }
            default -> { return OperationResult.fail("Unknown op"); }
        }
    }

    // ================= Sync & helpers =================

    @Override public List<LogEntry> getEntriesAfter(long fromSeq) {
        List<LogEntry> out = new ArrayList<>();
        for (var e : log.tailMap(fromSeq+1).entrySet()) {
            LogEntry le = e.getValue();
            if (le.committed) 
                out.add(new LogEntry(le.seqNo, le.op)); 
        }
        return out;
    }

    private String findLeaderName() throws Exception {
        Registry reg = LocateRegistry.getRegistry();
        frontend.FrontEndAdmin fe = (frontend.FrontEndAdmin) reg.lookup("FrontEnd");
        return fe.getCurrentSequencerName();
    }

    private ReplicatedAuction lookup(String rmiName) throws Exception {
        Registry reg = LocateRegistry.getRegistry();
        return (ReplicatedAuction) reg.lookup(rmiName);
    }

    @Override public long getLastCommittedSeqNo() { 
        return lastCommitted; 
    }

    // You may not use this method but it is here if you need it
    // Used to check if a replica is the leader
    @Override public boolean isSequencer() { 
        return isLeader; 
    }

    // Set the replica as a leader or not (isLeader is true: you are leader, isLeader is false: you are not)
    @Override public void setSequencer(boolean isLeader){ 
        this.isLeader = isLeader; 
        if (isLeader) {
            this.lastSeqAssigned = this.lastCommitted;
        }
    }
}
