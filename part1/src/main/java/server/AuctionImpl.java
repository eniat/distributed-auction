package server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AuctionImpl extends UnicastRemoteObject implements Auction {
    // TODO declare (thread-safe) state variables to keep track of users,items,auction owners, etc.
    private final Map<Integer, String> users;
    private final Map<Integer, AuctionItem> items;
    private final Set<Integer> closedItems ;
    private final Map<Integer, Integer> itemOwners ;
    private final Map<Integer, Integer> highestBidders;
    private final AtomicInteger userIdCounter;
    private final AtomicInteger itemIdCounter;

    public AuctionImpl() throws RemoteException { 
        super();
        // Initialised state variables
        users = new ConcurrentHashMap<>();
        items = new ConcurrentHashMap<>();
        closedItems = Collections.synchronizedSet(new HashSet<>());
        itemOwners = new ConcurrentHashMap<>();
        highestBidders = new ConcurrentHashMap<>();
        userIdCounter = new AtomicInteger(1);
        itemIdCounter = new AtomicInteger(1);
    }

    @Override
    public synchronized int register(String email) {
        // TODO:
        // - Allocate a new userID
        int newUserId = userIdCounter.getAndIncrement();
        users.put(newUserId, email);   
        // - Record mapping userID -> email
        // - Return the new userID
        return newUserId; // TODO: replace with allocated userID
    }

    @Override
    public synchronized int newAuction(int userID, AuctionSaleItem item) {
        // TODO:
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
        return newItemId; // TODO: replace with itemID or -1 on failure
    }

    @Override
    public synchronized AuctionItem getSpec(int itemID) {
        // TODO:
        // - Return the AuctionItem for itemID, or null if not found
        AuctionItem item = items.get(itemID);
        if (item == null) {
            return null;
        }
        return item;
    }

    @Override
    public synchronized AuctionItem[] listItems() {
        // TODO:
        // - Return all currently active items (do not return items from closed auctions).
        Collection<AuctionItem> vals = items.values();
        return vals.toArray(new AuctionItem[0]);
    }

    @Override
    public synchronized boolean bid(int userID, int itemID, int price) {
        // TODO:
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

    @Override
    public synchronized AuctionResult closeAuction(int userID, int itemID) {
        // TODO:
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
}
