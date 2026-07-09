package replica;

import common.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ReplicaMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ReplicaMain <id>");
            System.exit(1);
        }
        int id = Integer.parseInt(args[0]);
        String name = "replica" + id;

        // Register the replica with rmiregistry
        ReplicatedAuction r = new ReplicaImpl(id, name);
        Registry reg = LocateRegistry.getRegistry();
        reg.rebind(name, r);
        System.out.println("Replica " + id + " bound as " + name);

        // Ask the front-end to find out who the current sequencer (leader) is. If the answer is null (no leader), then register with the front-end (registerReplica)
        frontend.FrontEndAdmin fe = (frontend.FrontEndAdmin) reg.lookup("FrontEnd");
        String sequencerName = fe.getCurrentSequencerName();

        if (sequencerName != null && !sequencerName.equals(name)) {
            // Retrieve any missing committed log entries from the leader 
            ReplicatedAuction leader = (replica.ReplicatedAuction) reg.lookup(sequencerName);
            long lastLeaderCommitted = leader.getLastCommittedSeqNo();

            if (lastLeaderCommitted > 0) {
                java.util.List<LogEntry> missingEntries = leader.getEntriesAfter(0);
                for (LogEntry entry : missingEntries) {
                    r.propose(entry.seqNo, entry.op);
                }
                // Locally execute any new committed (previously missing) operations that were added to the log in the previous step
                r.commitUpTo(lastLeaderCommitted);
            }
        } 
        // Now that the replica is ready to serve requests, register with the front-end (front-end maintains replica membership) 
        fe.registerReplica(id, name);
        Thread.sleep(Long.MAX_VALUE);

    }
}
