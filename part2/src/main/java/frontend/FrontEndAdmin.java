package frontend;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface FrontEndAdmin extends Remote {
    String getCurrentSequencerName() throws RemoteException;
    void registerReplica(int id, String rmiName) throws RemoteException;
}
