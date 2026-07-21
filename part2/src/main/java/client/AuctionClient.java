package client;

import frontend.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

// A sample client which performs basic checks

public class AuctionClient {
    public static void main(String[] args) {
        ManagedChannel ch = ManagedChannelBuilder.forAddress("localhost", 50055)
                .usePlaintext().build();
        var stub = AuctionServiceGrpc.newBlockingStub(ch);

        // Register three users
        int alice = stub.register(RegisterRequest.newBuilder().setEmail("alice@example.com").build()).getUserId();
        int bob   = stub.register(RegisterRequest.newBuilder().setEmail("bob@example.com").build()).getUserId();
        int carol = stub.register(RegisterRequest.newBuilder().setEmail("carol@example.com").build()).getUserId();
        System.out.printf("Users -> alice=%d bob=%d carol=%d%n", alice, bob, carol);
 
        // Start a few auctions using one or more of the registered users.
        System.out.println("Starting auctions");
        //    - Construct and send NewAuctionRequest messages.
        int item1 = stub.newAuction(NewAuctionRequest.newBuilder().setUserId(alice).setName("thing1")
                .setDescription("first thing").setReservePrice(10).build()).getItemId();
        int item2 = stub.newAuction(NewAuctionRequest.newBuilder().setUserId(bob).setName("thing2")
                .setDescription("second thing").setReservePrice(20).build()).getItemId();
        //    - Print returned item IDs.
        System.out.printf("Auctions: item1=%d item2=%d%n", item1, item2);
        //
        // Have multiple users place bids on these items.
        //    - Use BidRequest messages.
        System.out.println("Placing bids");
        boolean bid1 = stub.bid(BidRequest.newBuilder().setUserId(bob).setItemId(item1).setPrice(15).build()).getSuccess();
        boolean bid2 = stub.bid(BidRequest.newBuilder().setUserId(carol).setItemId(item1).setPrice(8).build()).getSuccess();
        boolean bid3 = stub.bid(BidRequest.newBuilder().setUserId(alice).setItemId(item2).setPrice(25).build()).getSuccess();
        //    - Print whether each bid was accepted or rejected.
        System.out.printf("Bids: bob for item1(15)=%b, carol for item1(8)=%b, alice foritem2(25)=%b%n", bid1, bid2, bid3);
        //
        // Test listing and inspecting items.
        //    - Call listItems() to verify current highest bids and reserve prices.
        System.out.println("Listing items");
        var listResp = stub.listItems(Empty.newBuilder().build());
        for (var item : listResp.getItemsList()) {
            System.out.printf("ItemID=%d Name=%s Desc=%s Reserve=%d HighestBid=%d%n",item.getItemId(), item.getName(), item.getDescription(), item.getReservePrice(), item.getHighestBid());
        }
        //    - Call getSpec() for a specific item.
        System.out.println("Getting spec for item1");
        var specResp = stub.getSpec(GetSpecRequest.newBuilder().setItemId(item1).build());
        System.out.printf("ItemID=%d Name=%s Desc=%s Reserve=%d HighestBid=%d%n",specResp.getItemId(), specResp.getName(), specResp.getDescription(), specResp.getReservePrice(), specResp.getHighestBid());
        //
        // Close an auction.
        //    - Ensure only the creator can close it.
        System.out.println("Closing auction for item1 by owner");
        frontend.AuctionResult closeRespOwner = stub.closeAuction(CloseRequest.newBuilder().setUserId(alice).setItemId(item1).build());
        //    - Print the returned AuctionResult.
        System.out.printf("Closed itemid=%d: WinningUser=%d Price=%d%n",closeRespOwner.getItemId(), closeRespOwner.getWinningUser(), closeRespOwner.getPrice());
        //
        // Try edge cases:
        //    - Bidding on a non-existent item.
        System.out.println("Bidding on non-existent item");
        boolean bidNonExist = stub.bid(BidRequest.newBuilder().setUserId(bob).setItemId(9999).setPrice(50).build()).getSuccess();
        System.out.printf("Bidding on non-existent item success=%b%n", bidNonExist);
        //    - Bidding below reserve price.
        System.out.println("Bidding below reserve price");
        boolean bidBelowReserve = stub.bid(BidRequest.newBuilder().setUserId(carol).setItemId(item2).setPrice(15).build()).getSuccess();
        System.out.printf("Bidding below reserve price success=%b%n", bidBelowReserve);
        //    - Closing an auction twice.
        System.out.println("Closing auction for item1 again");
        frontend.AuctionResult closeRespAgain = stub.closeAuction(CloseRequest.newBuilder().setUserId(alice).setItemId(item1).build());
        System.out.printf("Closing again itemid=%d: WinningUser=%d Price=%d%n",closeRespAgain.getItemId(), closeRespAgain.getWinningUser(), closeRespAgain.getPrice()); 
        //    - Closing an auction by a non-owner.
        System.out.println("Closing auction for item2 by non-owner");
        frontend.AuctionResult closeRespNonOwner = stub.closeAuction(CloseRequest.newBuilder().setUserId(carol).setItemId(item2).build());
        System.out.printf("Should be all zeros Closed itemid=%d: WinningUser=%d Price=%d%n",closeRespNonOwner.getItemId(), closeRespNonOwner.getWinningUser(), closeRespNonOwner.getPrice());
        //
        // Print a summary of expected vs. actual outcomes for basic validation.
        System.out.println("Finsihed tests");
        System.out.println("bob bid on item1 above reserve: expected=true actual=" + bid1);
        System.out.println("carol bid on item1 below reserve: expected=false actual=" + bid2);
        System.out.println("alice bid on item2 above reserve: expected=true actual=" + bid3);
        System.out.println("bob bid on non-existent item: expected=false actual=" + bidNonExist);
        System.out.println("carol bid on item2 below reserve: expected=false actual=" + bidBelowReserve);
        System.out.println("close auction for item1 by owner: expected=2 actual=" + closeRespOwner.getWinningUser());
        System.out.println("close auction for item1 again: expected=0 actual=" + closeRespAgain.getWinningUser());
        System.out.println("close auction for item2 by non-owner: expected=0 actual=" + closeRespNonOwner.getWinningUser());

        ch.shutdown();
    }
}
