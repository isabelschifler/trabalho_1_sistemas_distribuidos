package peer;

import java.io.PrintStream;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Processo par (peer). O MESMO programa é executado três vezes:
 *
 *   java peer.Peer PeerA
 *   java peer.Peer PeerB
 *   java peer.Peer PeerC
 */
public class Peer {

    private static final int PORTA_SN = 1099;

    private static final List<String> TODOS_OS_PEERS = Arrays.asList("PeerA", "PeerB", "PeerC");

    public static void main(String[] args) throws Exception {
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
        } catch (Exception e) {
            // segue com o encoding padrão se UTF-8 não estiver disponível
        }

        if (args.length != 1 || !TODOS_OS_PEERS.contains(args[0])) {
            System.out.println("Uso: java peer.Peer <PeerA|PeerB|PeerC>");
            return;
        }
        String meuNome = args[0];
        System.out.println("[" + meuNome + "] PID deste processo: " + ProcessHandle.current().pid());

        // 1. Serviço de nomes: só UM processo consegue criá-lo na porta 1099.
        Registry registry = obterOuCriarServicoDeNomes(meuNome);

        // 2. Objeto remoto deste peer (o par de chaves é gerado no construtor).
        PeerImpl meuPeer = new PeerImpl(meuNome);
        registry.rebind(meuNome, meuPeer);
        System.out.println("[" + meuNome + "] registrado no servico de nomes.");

        // 3 e 4. Espera os demais e guarda as referências remotas.
        List<String> outros = new ArrayList<>(TODOS_OS_PEERS);
        outros.remove(meuNome);
        for (String nomeOutro : outros) {
            InterfacePeer ref = aguardarPeer(registry, nomeOutro, meuNome);
            meuPeer.adicionarPeer(nomeOutro, ref);
            System.out.println("[" + meuNome + "] referencia de " + nomeOutro + " obtida.");
        }

        // 5. Troca de chaves públicas: envia a minha e espera receber as dos outros.
        meuPeer.distribuirChavePublica();
        meuPeer.aguardarChaves();

        menu(meuPeer, registry);
    }

    private static Registry obterOuCriarServicoDeNomes(String meuNome) throws RemoteException {
        try {
            Registry r = LocateRegistry.createRegistry(PORTA_SN);
            System.out.println("[" + meuNome + "] servico de nomes CRIADO na porta " + PORTA_SN + ".");
            return r;
        } catch (ExportException jaExiste) {
            Registry r = LocateRegistry.getRegistry(PORTA_SN);
            System.out.println("[" + meuNome + "] servico de nomes ja existia — referencia obtida.");
            return r;
        }
    }

    private static InterfacePeer aguardarPeer(Registry registry, String nomeOutro, String meuNome)
            throws RemoteException, InterruptedException {
        boolean avisou = false;
        while (true) {
            try {
                return (InterfacePeer) registry.lookup(nomeOutro);
            } catch (NotBoundException aindaNao) {
                if (!avisou) {
                    System.out.println("[" + meuNome + "] aguardando " + nomeOutro + " entrar...");
                    avisou = true;
                }
                Thread.sleep(500);
            }
        }
    }

    private static void menu(PeerImpl meuPeer, Registry registry) throws Exception {
        Scanner in = new Scanner(System.in, "UTF-8");
        String nome = meuPeer.getNome();
        while (true) {
            System.out.println();
            System.out.println("==== " + nome + " ====");
            System.out.println("1 - Pedir R1");
            System.out.println("2 - Pedir R2");
            System.out.println("3 - Liberar R1");
            System.out.println("4 - Liberar R2");
            System.out.println("5 - Mostrar estado, relogio e filas");
            System.out.println("6 - Mostrar log de mensagens (com verificacao de assinatura)");
            System.out.println("7 - Enviar pedido ADULTERADO (demonstra assinatura invalida)");
            System.out.println("8 - Enviar ola para os outros peers");
            System.out.println("9 - Listar nomes no servico de nomes (list)");
            System.out.println("0 - Sair");
            System.out.print("> ");
            if (!in.hasNextLine()) {
                break;
            }
            String op = in.nextLine().trim();
            switch (op) {
                case "1":
                    meuPeer.pedir("R1");
                    break;
                case "2":
                    meuPeer.pedir("R2");
                    break;
                case "3":
                    meuPeer.liberar("R1");
                    break;
                case "4":
                    meuPeer.liberar("R2");
                    break;
                case "5":
                    meuPeer.mostrarEstado();
                    break;
                case "6":
                    meuPeer.mostrarLog();
                    break;
                case "7":
                    meuPeer.enviarPedidoAdulterado();
                    break;
                case "8":
                    meuPeer.enviarOlaParaTodos();
                    break;
                case "9":
                    try {
                        System.out.println("Registrados: " + Arrays.toString(registry.list()));
                    } catch (RemoteException e) {
                        System.out.println("Servico de nomes indisponivel: " + e.getMessage());
                    }
                    break;
                case "0":
                    try {
                        registry.unbind(nome);
                    } catch (Exception semServicoDeNomes) {
                        // o peer que hospedava o serviço de nomes já saiu
                    }
                    UnicastRemoteObject.unexportObject(meuPeer, true);
                    System.out.println("[" + nome + "] encerrado.");
                    System.exit(0);
                    break;
                default:
                    System.out.println("Opcao invalida.");
            }
        }
    }
}