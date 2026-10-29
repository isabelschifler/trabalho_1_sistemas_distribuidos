package peer;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.PublicKey;

/**
 * Interface remota ÚNICA de um processo par (peer).
 * Todo peer expõe estes métodos e chama os métodos dos outros.
 * o que um peer aceita receber
 */
public interface InterfacePeer extends Remote {

    /** Devolve o nome com que este peer se registrou no serviço de nomes. */
    String getNome() throws RemoteException;

    /** Mensagem de teste (unicast). */
    void ola(String remetente) throws RemoteException;

    /** Recebe a chave pública de outro peer (usada depois para conferir as assinaturas). */
    void receberChavePublica(String remetente, PublicKey chave) throws RemoteException;

    /**
     * Recebe um PEDIDO de acesso a um recurso (Ricart e Agrawala).
     * A assinatura cobre o texto "PEDIDO|remetente|recurso|timestamp".
     */
    void solicitarRecurso(String remetente, String recurso, long timestamp, byte[] assinatura)
            throws RemoteException;

    /**
     * Recebe a notificação "recurso liberado / pode prosseguir" (papel de subscriber).
     * Essa notificação é a permissão (OK) do algoritmo de Ricart e Agrawala.
     * A assinatura cobre o texto "LIBERADO|remetente|recurso|timestamp".
     */
    void notificar(String remetente, String recurso, long timestamp, byte[] assinatura)
            throws RemoteException;
}