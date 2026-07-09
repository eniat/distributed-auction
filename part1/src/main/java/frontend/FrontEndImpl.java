package frontend;

import io.grpc.stub.StreamObserver;
import server.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class FrontEndImpl extends AuctionServiceGrpc.AuctionServiceImplBase {
    private final Auction auction;

    public FrontEndImpl() throws Exception {
        Registry reg = LocateRegistry.getRegistry();
        this.auction = (Auction) reg.lookup("AuctionServer");
    }

    @Override
    public void register(RegisterRequest req, StreamObserver<RegisterReply> resp) {
        try {
            int id = auction.register(req.getEmail());
            resp.onNext(RegisterReply.newBuilder().setUserId(id).build());
            resp.onCompleted();
        } catch (Exception e) { resp.onError(e); }
    }

    @Override
    public void newAuction(NewAuctionRequest req, StreamObserver<NewAuctionReply> resp) {
        // Construct an AuctionSaleItem from the gRPC request fields.
        try {
            // Forward newAuction(userId, item) to the RMI Auction server.
            AuctionSaleItem item = new AuctionSaleItem(req.getName(), req.getDescription(), req.getReservePrice());
            int itemId = auction.newAuction(req.getUserId(), item);
            // Build and return a NewAuctionReply with the created itemId.
            resp.onNext(NewAuctionReply.newBuilder().setItemId(itemId).build());
            resp.onCompleted();
        } catch (Exception e) { resp.onError(e);}
    }

    @Override
    public void bid(BidRequest req, StreamObserver<BidReply> resp) {
        // Forward bid(userId, itemId, price) to the RMI Auction server.
        try {
            boolean success = auction.bid(req.getUserId(), req.getItemId(), req.getPrice());
            // Build and return a BidReply with success=true/false.
            resp.onNext(BidReply.newBuilder().setSuccess(success).build());
            resp.onCompleted();
        } catch (Exception e) { resp.onError(e);}
    }

    @Override
    public void listItems(Empty req, StreamObserver<ListReply> resp) {
        try{
            // Call auction.listItems() on the RMI server.
            AuctionItem[] items = auction.listItems();
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
        } catch (Exception e) { resp.onError(e);}
    
    }

    @Override
    public void getSpec(GetSpecRequest req, StreamObserver<Item> resp) {
        try {
            // Call auction.getSpec(itemId) on the RMI server.
            AuctionItem ai = auction.getSpec(req.getItemId());
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

        } catch (Exception e) { resp.onError(e);}
    }

    @Override
    public void closeAuction(CloseRequest req, StreamObserver<AuctionResult> resp) {
        try{
            // Forward closeAuction(userId, itemId) to the RMI Auction server.
            server.AuctionResult result = auction.closeAuction(req.getUserId(), req.getItemId());
            AuctionResult reply;
            // If the result is null (e.g., wrong owner or already closed), return
            // an AuctionResult with zeroed fields.
            if (result == null) {
                reply = AuctionResult.newBuilder().setItemId(0).setPrice(0).setWinningUser(0).build();
            } else {
                // Otherwise, map the AuctionResult fields and return them.
                reply = AuctionResult.newBuilder().setItemId(result.itemID).setWinningUser(result.winningUser).setPrice(result.price).build();
            }
            resp.onNext(reply);
            resp.onCompleted();
        } catch (Exception e) { resp.onError(e);}

    }
}
